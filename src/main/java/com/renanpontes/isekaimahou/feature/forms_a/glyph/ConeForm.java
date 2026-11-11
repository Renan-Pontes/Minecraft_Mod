package com.renanpontes.isekaimahou.feature.forms_a.glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.forms_a.FormAim;
import com.renanpontes.isekaimahou.feature.forms_a.FormsFx;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Cone: leque de 60° à frente, alcance 6 blocos (× área). Aplica em todos no cone que estejam à vista.
 * Expandir = mais longe e mais aberto; Concentrar = mais curto e fechado; Fantasma = ignora paredes;
 * Sensível = também afeta os blocos que o leque toca.
 * <p>
 * Estágios (v0.7): abre a partir do ponto de origem do estágio, na direção dele, sem atingir o alvo que o estágio
 * anterior acabou de acertar; com Saltar, o leque "avança" até perto do próximo inimigo. Avisa {@code impact} em cada
 * criatura atingida (a mais perto primeiro) e {@code end} na ponta do leque.
 */
public class ConeForm extends FormGlyph {
    private static final float BASE_RANGE = 6.0F;
    private static final double BASE_HALF_ANGLE = 30.0;

    public ConeForm() {
        super("cone", 6);
        element(Element.ARCANE);
        tier(1);
    }

    @Override
    public boolean stageable() { return true; }

    @Override
    public int baseCooldown() { return 14; }

    @Override
    public void cast(SpellContext ctx) {
        if (!FormAim.casterReady(ctx)) return;
        LivingEntity caster = ctx.caster();
        SpellStats s = ctx.formStats();
        float range = s.radius(BASE_RANGE);
        double halfDeg = BASE_HALF_ANGLE + 7.5 * Math.max(0, s.aoe);
        if (s.concentrate) halfDeg *= 0.65;
        halfDeg = Math.min(70.0, halfDeg);
        double half = Math.toRadians(halfDeg);
        double cosHalf = Math.cos(half);

        Vec3 aim = FormAim.aimDir(ctx);
        Vec3 origin;
        if (ctx.hasOrigin()) {
            origin = FormAim.closeIn(ctx, ctx.eyePos(), aim, range);
            FormsFx.dash(ctx, ctx.eyePos(), origin);
        } else {
            origin = ctx.eyePos().subtract(0, 0.2, 0);
        }
        FormAim.Frame frame = FormAim.Frame.of(aim);

        // Entidades no cone (centro, pés ou cabeça dentro do ângulo e à vista), da mais perto para a mais longe
        List<Entity> inside = new ArrayList<>();
        for (Entity e : ctx.level().getEntities(caster, new AABB(origin, origin).inflate(range + 1.0),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && !ctx.ignores(e))) {
            if (FormAim.affects(ctx, e) && inCone(ctx, s, origin, frame.forward(), cosHalf, range, e)) inside.add(e);
        }
        inside.sort(Comparator.comparingDouble(e -> e.distanceToSqr(origin)));
        for (Entity e : inside) {
            Vec3 c = FormAim.center(e);
            Vec3 d = c.subtract(origin);
            // Impacto real em cada alvo (Ao Acertar nasce no primeiro; Saltar parte dele)
            ctx.impact(c, d.lengthSqr() > 1.0E-4 ? d.normalize() : frame.forward(), e);
            ctx.resolveOnEntity(e, null);
            FormsFx.burst(ctx, c, 6, 0.2);
        }

        // Blocos tocados pelo leque (só os efeitos que mexem em blocos ou com Sensível) — raspão, não é impacto
        Set<BlockPos> done = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            Vec3 dir = frame.dir(-half + 2 * half * i / 4.0, 0);
            BlockHitResult bh = FormAim.clipBlocks(ctx, origin, origin.add(dir.scale(range)), caster);
            if (bh.getType() == HitResult.Type.BLOCK && done.add(bh.getBlockPos())) ctx.resolveOnBlock(bh, null);
        }

        drawFan(ctx, origin, frame, half, range);
        FormsFx.sound(ctx, origin, SoundEvents.FIRECHARGE_USE, 0.6F, 1.25F);
        FormsFx.sound(ctx, origin, SoundEvents.ILLUSIONER_CAST_SPELL, 0.5F, 1.5F);
        // O leque acaba na ponta: Ao Terminar nasce lá, na mesma direção
        ctx.end(origin.add(frame.forward().scale(range)), frame.forward());
    }

    private static boolean inCone(SpellContext ctx, SpellStats s, Vec3 origin, Vec3 fwd, double cosHalf, double range, Entity e) {
        Vec3[] probes = {
                FormAim.center(e),
                e.position().add(0, Math.min(0.3, e.getBbHeight() * 0.3), 0),
                e.position().add(0, e.getBbHeight() * 0.9, 0),
                FormAim.closestPoint(e, origin)
        };
        for (Vec3 p : probes) {
            Vec3 d = p.subtract(origin);
            double len = d.length();
            if (len > range) continue;
            // Encostado na origem: conta mesmo fora do ângulo (senão o leque "pula" quem está grudado)
            if (len > 1.2 && d.scale(1.0 / len).dot(fwd) < cosHalf) continue;
            if (s.ghost || FormAim.clear(ctx, origin, p, ctx.caster())) return true;
        }
        return false;
    }

    /** Leque de partículas: arcos de pó colorido em camadas + jatos da partícula do elemento saindo para frente. */
    private static void drawFan(SpellContext ctx, Vec3 origin, FormAim.Frame frame, double half, double range) {
        ServerLevel level = ctx.level();
        DustParticleOptions dust = FormsFx.dust(ctx.color(), 1.2F);
        DustParticleOptions bright = FormsFx.dust(FormsFx.lighten(ctx.color(), 0.5F), 0.8F);
        ParticleOptions sig = FormsFx.jet(ctx.element());
        for (double d = 1.0; d <= range + 0.01; d += 0.9) {
            int n = Math.max(3, (int) (d * 2 * half * 1.5));
            for (int j = 0; j < n; j++) {
                double yaw = -half + 2 * half * j / (n - 1);
                for (int k = -1; k <= 1; k++) {
                    double pitch = k * half * 0.45 + (ctx.random().nextDouble() - 0.5) * 0.1;
                    Vec3 p = origin.add(frame.dir(yaw, pitch).scale(d));
                    FormsFx.send(level, (j + k) % 2 == 0 ? dust : bright, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
                }
            }
        }
        for (int i = 0; i < 14; i++) {
            double yaw = (ctx.random().nextDouble() * 2 - 1) * half;
            double pitch = (ctx.random().nextDouble() * 2 - 1) * half * 0.5;
            Vec3 dir = frame.dir(yaw, pitch);
            FormsFx.moving(level, sig, origin.add(dir.scale(0.8)), dir, 0.35 + range * 0.04);
        }
    }
}
