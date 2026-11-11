package com.renanpontes.isekaimahou.feature.class_quincy.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.feature.class_quincy.Quincy;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyFx;
import com.renanpontes.isekaimahou.feature.class_quincy.entity.SpiritArrowEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Heilig Bogen (homenagem: o Arco Sagrado). Depois de 1 s armando, um ARCO DE LUZ GIGANTE se abre na sua frente e
 * dispara uma flecha-lança central enorme (perfura 4) e um leque de 4 flechas grandes (perfuram 3) — +2 flechas por
 * Dividir; cada flecha é uma cópia com os efeitos da magia. Acelerar/Perfurar/Teleguiado/Expandir valem.
 * Rank IV do Auswählen.
 */
public class HeiligBogenForm extends FormGlyph {
    public HeiligBogenForm() {
        super("quincy_heilig_bogen", 24);
        origin(MagicPath.QUINCY);
        element(Element.LIGHT);
        tier(4);
        minCircle(5);
    }

    private static int arrows(SpellStats s) {
        return 5 + 2 * Math.max(0, s.split);
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return arrows(stats);
    }

    @Override
    public int baseCastTicks() {
        return 20;
    }

    @Override
    public int baseCooldown() {
        return 80;
    }

    @Override
    public float hintRange() {
        return 64F;
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        Vec3 look = ctx.look();
        Vec3 origin = ctx.hasOrigin() ? ctx.eyePos() : caster.getEyePosition().add(look.scale(1.2)).add(0, -0.1, 0);
        giantBow(level, origin, look);
        com.renanpontes.isekaimahou.feature.class_quincy.QuincyFxNet.burst(caster, com.renanpontes.isekaimahou.feature.class_quincy.QuincyFxNet.BOGEN,
                origin, look, 1.8F, com.renanpontes.isekaimahou.feature.class_quincy.Quincy.REISHI_RGB);
        int n = arrows(s);
        float speed = 3.6F * s.speedMult();
        for (int i = 0; i < n; i++) {
            boolean center = i == 0;
            float yaw = center ? 0F : ((i % 2 == 1 ? 1 : -1) * ((i + 1) / 2) * 5.0F);
            float pitch = center ? 0F : (i % 3 - 1) * 1.5F;
            Vec3 d = QuincyGlyphs.rotate(look, yaw, pitch);
            SpiritArrowEntity a = SpiritArrowEntity.spell(ctx, caster, origin).pierce(center ? 4 : 3).size(center ? 3.2F : 2.0F).life(80);
            a.shoot(d.x, d.y, d.z, center ? speed * 1.15F : speed, 0.2F);
            level.addFreshEntity(a);
        }
        QuincyFx.sound(level, origin, SoundEvents.CROSSBOW_SHOOT, 1.2F, 0.6F);
        QuincyFx.sound(level, origin, SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.6F);
        QuincyFx.sound(level, origin, SoundEvents.TRIDENT_THUNDER.value(), 0.4F, 1.8F);
        if (caster instanceof ServerPlayer sp && !s.silent && ctx.stage() == 0) {
            Announce.send(sp, Quincy.msg("bogen_shout"), 0xBFE8FF, Announce.STYLE_SPELL);
        }
    }

    /** O arco gigante de luz (3 blocos) com a corda puxada e o brilho na ponta da flecha central. */
    static void giantBow(ServerLevel level, Vec3 c, Vec3 look) {
        Vec3 side = new Vec3(-look.z, 0, look.x);
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(look).normalize();
        Vec3 top = null, bottom = null;
        for (int i = 0; i <= 30; i++) {
            double t = -1 + 2 * i / 30.0;
            Vec3 at = c.add(up.scale(t * 1.8)).add(look.scale((1 - t * t) * 0.7));
            level.sendParticles(i % 4 == 0 ? QuincyFx.WHITE : QuincyFx.REISHI, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            if (i == 0) bottom = at;
            if (i == 30) top = at;
        }
        Vec3 nock = c.subtract(look.scale(0.6));
        if (top != null) QuincyFx.line(level, top, nock, QuincyFx.WHITE, 0.25);
        if (bottom != null) QuincyFx.line(level, bottom, nock, QuincyFx.WHITE, 0.25);
        // asas da cruz nas pontas do arco
        for (Vec3 tip : new Vec3[]{top, bottom}) {
            if (tip == null) continue;
            level.sendParticles(QuincyFx.WHITE, tip.x, tip.y, tip.z, 6, 0.15, 0.15, 0.15, 0.01);
        }
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 12, 0.3, 0.3, 0.3, 0.06);
        level.sendParticles(ParticleTypes.FLASH, c.x + look.x, c.y + look.y, c.z + look.z, 1, 0, 0, 0, 0);
    }
}
