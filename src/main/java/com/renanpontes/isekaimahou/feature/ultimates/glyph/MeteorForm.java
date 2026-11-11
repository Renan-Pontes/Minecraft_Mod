package com.renanpontes.isekaimahou.feature.ultimates.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.ultimates.MeteorImpact;
import com.renanpontes.isekaimahou.feature.ultimates.UltFx;
import com.renanpontes.isekaimahou.feature.ultimates.entity.MeteorEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Meteoro (forma, estágio): uma fenda se abre no céu sobre o ponto mirado (até 48 blocos, ou a âncora do estágio) e
 * uma rocha em brasa despenca na diagonal, com rastro de fogo e fumaça, enquanto o alvo fica marcado no chão.
 * No impacto: efeitos da magia em raio 4 (+Expandir), dano de choque, onda de choque, tremor e cratera.
 * Amplificar/Carregar = rocha maior (cratera maior) · Dividir = mais rochas espalhadas · Acelerar = cai mais rápido.
 */
public class MeteorForm extends FormGlyph {
    public static final double RANGE = 48.0;
    /** Altura de onde a rocha nasce (céu aberto). */
    public static final float SKY = 34F;

    public MeteorForm() {
        super("meteor", 18);
        element(Element.ARCANE);
        tier(3);
        minCircle(3);
    }

    @Override
    public boolean stageable() { return true; }

    @Override
    public int baseCastTicks() { return 12; }

    @Override
    public int baseCooldown() { return 50; }

    @Override
    public void cast(SpellContext ctx) {
        if (!ctx.hasOrigin() && !UltFx.casterReady(ctx)) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        Vec3 target = UltFx.aimGround(ctx, RANGE);
        int count = Mth.clamp(s.count(), 1, 6);
        float size = Mth.clamp(1.0F + 0.8F * (s.power() - 1F) + 0.2F * Math.max(0, s.aoe), 0.8F, 3.5F);
        float radius = Mth.clamp(s.radius(4F), 2F, 16F);
        float crater = Mth.clamp(2.2F + 1.8F * size, 2.5F, 9F);
        int rgb = UltFx.rgb(ctx);
        Vec3 from = ctx.eyePos();
        RandomSource rnd = level.random;

        for (int i = 0; i < count; i++) {
            Vec3 point = target;
            if (i > 0) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double d = radius * (0.6 + rnd.nextDouble() * 0.8);
                point = UltFx.groundBelow(level, target.add(Math.cos(a) * d, 4, Math.sin(a) * d), 16);
            }
            Vec3 p = point;
            float sz = i == 0 ? size : size * (0.75F + rnd.nextFloat() * 0.25F);
            int delay = i * 4;
            boolean last = i == count - 1;
            Runnable spawn = () -> {
                MeteorEntity m = launch(ctx, level, from, p, sz, rgb, radius, crater * (sz / size), s.speedMult());
                if (last) m.endsStage();
            };
            if (delay <= 0) spawn.run();
            else SpellScheduler.schedule(delay, spawn);
        }

        UltFx.sound(level, target, SoundEvents.EVOKER_CAST_SPELL, 1.4F, 0.6F);
        if (!ctx.hasOrigin() && ctx.caster() != null) {
            ctx.caster().swing(InteractionHand.MAIN_HAND, true);
            Vec3 eye = ctx.caster().getEyePosition();
            UltFx.far(level, ParticleTypes.FLAME, eye.x, eye.y + 0.3, eye.z, 10, 0.3, 0.3, 0.3, 0.05);
        }
    }

    /** Abre a fenda no céu, marca o chão e solta a rocha. */
    static MeteorEntity launch(SpellContext ctx, ServerLevel level, Vec3 caster, Vec3 point, float size, int rgb, float radius,
                               float crater, float speedMult) {
        Vec3 start = skyStart(level, caster, point, SKY + 4F * size);
        float speed = 1.45F * speedMult;
        int fall = Math.max(6, (int) Math.ceil(start.distanceTo(point) / speed));
        MeteorEntity m = MeteorEntity.create(ctx, start, point, fall, size, UltFx.mix(MeteorImpact.FIRE, rgb, 0.3F), radius, crater,
                3.5F + 1.5F * size, false, 1F);
        level.addFreshEntity(m);
        Telegraph.show(level, Telegraph.Shape.circle(point, radius).warmup(fall).color(UltFx.mix(MeteorImpact.FIRE, rgb, 0.4F)));
        riftFx(level, start, size, rgb);
        return m;
    }

    /**
     * Ponto de partida: atrás do alvo na direção de quem conjurou (a rocha vem na diagonal, por cima do conjurador) e
     * {@code height} acima. Com teto no caminho, nasce logo abaixo dele (mín. 5 blocos acima do alvo).
     */
    static Vec3 skyStart(ServerLevel level, Vec3 caster, Vec3 point, float height) {
        Vec3 flat = caster.subtract(point).multiply(1, 0, 1);
        Vec3 back = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.6, 0, 0.8) : flat.normalize();
        Vec3 want = point.add(back.scale(height * 0.45)).add(0, height, 0);
        Vec3 start = belowCeiling(level, point, want);
        // Teto baixo no caminho diagonal: tenta cair na vertical (mais espaço em salões/cavernas)
        if (start.y - point.y < 5) {
            Vec3 up = belowCeiling(level, point, point.add(0, height, 0));
            if (up.y > start.y) start = up;
        }
        return start;
    }

    /**
     * Ponto de partida de uma rocha que vai de {@code want} até {@code point}: o próprio {@code want} se o caminho está
     * livre; senão 1,5 bloco antes do primeiro bloco (nunca dentro da rocha — debaixo da terra ela nasce logo abaixo do
     * teto, em vez de nascer dentro dele e explodir lá em cima).
     */
    public static Vec3 belowCeiling(ServerLevel level, Vec3 point, Vec3 want) {
        BlockHitResult bh = UltFx.clip(level, point.add(0, 0.5, 0), want, ClipContext.Fluid.NONE, null);
        if (bh.getType() != HitResult.Type.BLOCK) return want;
        double total = want.distanceTo(point);
        if (total < 1.0E-3) return point;
        double frac = Mth.clamp((bh.getLocation().distanceTo(point) - 1.5) / total, 0.0, 1.0);
        return point.lerp(want, frac);
    }

    /** A "fenda": anel de portal invertido, brasas e o estalo do céu se abrindo. */
    static void riftFx(ServerLevel level, Vec3 at, float size, int rgb) {
        double r = 1.2 + size * 0.6;
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            UltFx.far(level, ParticleTypes.REVERSE_PORTAL, at.x + Math.cos(a) * r, at.y, at.z + Math.sin(a) * r, 2, 0.05, 0.05, 0.05, 0.02);
        }
        UltFx.far(level, ParticleTypes.FLAME, at.x, at.y, at.z, 16, r * 0.4, 0.2, r * 0.4, 0.05);
        UltFx.far(level, new DustParticleOptions(Vec3.fromRGB24(rgb).toVector3f(), 2.2F), at.x, at.y, at.z, 14, r * 0.5, 0.2, r * 0.5, 0);
        UltFx.far(level, ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 10, r * 0.6, 0.3, r * 0.6, 0.01);
        UltFx.sound(level, at, SoundEvents.WITHER_SHOOT, 2.5F, 0.45F);
        UltFx.sound(level, at, SoundEvents.FIRECHARGE_USE, 2.5F, 0.6F);
    }
}
