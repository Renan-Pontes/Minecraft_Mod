package com.renanpontes.isekaimahou.feature.class_shinigami.glyph;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.feature.class_shinigami.Shinigami;
import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiFx;
import com.renanpontes.isekaimahou.feature.class_shinigami.entity.BlackCoffinEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Hadō nº 90 — Kurohitsugi (homenagem: o Caixão Negro). Depois de um encantamento de 1,5 s, um caixão de Reiatsu negro
 * se ergue em volta do inimigo mirado (até 24 blocos, +4 por Acelerar; sem alvo, no ponto mirado): prende quem está
 * dentro (Enraizado + Atordoado; chefes por 1/3 do tempo), quatro rodadas de lanças de trevas aplicam os efeitos da
 * magia e no fim o caixão se ESMAGA (dano próprio 7 + efeitos). Expandir alarga o caixão. Custo: 4 cópias (as lanças).
 * Conta como Kidō. Rank IV + 200 Kidō.
 */
public class HadoKurohitsugiForm extends FormGlyph {
    public HadoKurohitsugiForm() {
        super("kido_hado_90", 30);
        origin(MagicPath.SHINIGAMI);
        element(Element.SHADOW);
        tier(4);
        minCircle(7);
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return 4F;
    }

    @Override
    public int baseCastTicks() {
        return 30;
    }

    @Override
    public int baseCooldown() {
        return 200;
    }

    @Override
    public float hintRange() {
        return 24F;
    }

    @Override
    public float hintRadius() {
        return 1.5F;
    }

    @Override
    public int hintDuration() {
        return BlackCoffinEntity.LIFE;
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        double range = 24 + 4 * Math.max(0, s.speed);
        Vec3 from = ctx.eyePos();
        Vec3 look = ctx.look();
        LivingEntity target = aim(ctx, from, look, range);
        Vec3 at;
        float width, height;
        float grow = 1F + 0.25F * Math.max(0, s.aoe);
        if (target != null) {
            at = target.position();
            width = (Math.max(1.6F, target.getBbWidth() + 1.2F)) * grow;
            height = (target.getBbHeight() + 1.0F) * Math.min(1.5F, grow);
        } else {
            Vec3 to = from.add(look.scale(range));
            HitResult r = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            at = r.getType() == HitResult.Type.BLOCK ? r.getLocation() : to;
            width = 2.2F * grow;
            height = 3.0F;
        }
        level.addFreshEntity(BlackCoffinEntity.of(ctx, at, width, height, target));
        // o brasão de Kidō no chão e o lampejo na mão
        ShinigamiFx.ring(level, at.add(0, 0.1, 0), width * 0.9, 28, 0F, ShinigamiFx.BLACK);
        ShinigamiFx.ring(level, at.add(0, 0.1, 0), width * 0.6, 20, 0.5F, ShinigamiFx.dust(0x7A2AC8, 1.1F));
        ShinigamiFx.sound(level, at, SoundEvents.EVOKER_PREPARE_SUMMON, 1.0F, 0.5F);
        ShinigamiFx.sound(level, from, SoundEvents.SOUL_ESCAPE.value(), 1.0F, 0.6F);
        if (caster != null && !ctx.hasOrigin()) caster.swing(net.minecraft.world.InteractionHand.OFF_HAND, true);
        if (caster instanceof ServerPlayer sp && !s.silent && ctx.stage() == 0) {
            Announce.send(sp, Shinigami.msg("hado90_shout"), 0x7A2AC8, Announce.STYLE_SPELL);
        }
    }

    /** O inimigo na mira (raio que passa perto conta), ou null. */
    @Nullable
    private static LivingEntity aim(SpellContext ctx, Vec3 from, Vec3 look, double range) {
        ServerLevel level = ctx.level();
        Vec3 to = from.add(look.scale(range));
        HitResult r = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx.caster()));
        if (r.getType() == HitResult.Type.BLOCK) to = r.getLocation();
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.5), LivingEntity::isAlive)) {
            if (e == ctx.caster() || ctx.isAlly(e) || ctx.ignores(e)) continue;
            if (e.getBoundingBox().inflate(0.8).clip(from, to).isEmpty()) continue;
            double d = e.distanceToSqr(from);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }
}
