package com.renanpontes.isekaimahou.feature.class_quincy.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.feature.class_quincy.Quincy;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyFx;
import com.renanpontes.isekaimahou.feature.class_quincy.entity.LightCageEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Sankt Zwinger (homenagem: a Gaiola Sagrada). Cinco pilares de luz caem na estrela Quincy no ponto mirado (até 24
 * blocos) e erguem uma GAIOLA de grades de Reishi (raio 5, +1,5 por Expandir; 6 de altura) por 6 s (Estender
 * aumenta): inimigos lá dentro não conseguem sair e, a cada 0,5 s, lâminas de luz RETALHAM todos eles com os efeitos
 * da magia. Custo: pulsos × duração, de forma moderada. Rank IV + 80 usos da Letra.
 */
public class SanktZwingerForm extends FormGlyph {
    public static final int DURATION = 120;

    public SanktZwingerForm() {
        super("quincy_sankt_zwinger", 30);
        origin(MagicPath.QUINCY);
        element(Element.LIGHT);
        tier(4);
        minCircle(6);
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return SpellCost.pulseCopies(stats.duration(DURATION), LightCageEntity.PULSE);
    }

    @Override
    public int baseCastTicks() {
        return 16;
    }

    @Override
    public int baseCooldown() {
        return 300;
    }

    @Override
    public int hintDuration() {
        return DURATION;
    }

    @Override
    public float hintRadius() {
        return 5F;
    }

    @Override
    public float hintRange() {
        return 24F;
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        Vec3 at;
        if (ctx.hasOrigin() && ctx.anchor() != null) {
            at = ctx.anchor();
        } else {
            Vec3 from = ctx.eyePos();
            Vec3 to = from.add(ctx.look().scale(24));
            HitResult r = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            at = r.getType() == HitResult.Type.BLOCK ? r.getLocation() : to;
        }
        // assenta no chão (até 6 blocos abaixo)
        BlockPos p = BlockPos.containing(at);
        for (int i = 0; i < 6 && level.getBlockState(p.below()).isAir(); i++) p = p.below();
        Vec3 ground = new Vec3(at.x, p.getY(), at.z);
        float radius = 5F + 1.5F * Math.max(0, s.aoe);
        int life = s.duration(DURATION);
        level.addFreshEntity(LightCageEntity.of(ctx, ground, radius, 6F, life));
        com.renanpontes.isekaimahou.feature.class_quincy.QuincyFxNet.burstNear(level, com.renanpontes.isekaimahou.feature.class_quincy.QuincyFxNet.ZWINGER,
                ground, Vec3.ZERO, radius, 0xE8F8FF, caster.getId());
        // os cinco pilares caindo do céu nas pontas da estrela
        for (int k = 0; k < 5; k++) {
            double a = -Math.PI / 2 + k * Math.PI * 2 / 5;
            Vec3 b = ground.add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            QuincyFx.line(level, b.add(0, 10, 0), b, QuincyFx.WHITE, 0.4);
            QuincyFx.pillar(level, b, 7.0, 30);
        }
        QuincyFx.pentacle(level, ground.add(0, 0.08, 0), radius, 0F);
        QuincyFx.sound(level, ground, SoundEvents.BEACON_ACTIVATE, 1.2F, 1.3F);
        QuincyFx.sound(level, ground, SoundEvents.CHAIN_PLACE, 1.0F, 0.6F);
        QuincyFx.sound(level, ground, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.8F);
        if (caster instanceof ServerPlayer sp && !s.silent && ctx.stage() == 0) {
            Announce.send(sp, Quincy.msg("zwinger_shout"), 0xE8F8FF, Announce.STYLE_SPELL);
        }
    }
}
