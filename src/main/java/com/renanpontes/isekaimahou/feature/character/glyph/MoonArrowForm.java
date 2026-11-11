package com.renanpontes.isekaimahou.feature.character.glyph;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.character.RaceFx;
import com.renanpontes.isekaimahou.glyph.CoreGlyphs;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Flecha Lunar (Elfo): uma flecha de luz prateada que sai da mão, rápida e certeira — curva sozinha um pouco em direção
 * ao inimigo à frente (Teleguiado curva muito mais). À noite, sob a lua, os efeitos ficam 20% mais fortes.
 * Dividir = mais flechas em leque; Espalhar; Perfurar; Ricochete; Fantasma (atravessa blocos); Acelerar.
 * <p>
 * Projétil simulado no servidor (sem entidade): anda pelo {@link SpellScheduler} no máx. {@value #LIFE} ticks, com raio de
 * colisão contra blocos e criaturas a cada passo. Estágios (v0.7): sai da origem do contexto; avisa {@code impact} em
 * cada acerto e {@code end} quando some.
 */
public class MoonArrowForm extends FormGlyph {
    private static final int LIFE = 26;
    private static final double SPEED = 2.4;
    private static final double TRACK_RANGE = 22.0;
    private static final int SILVER = 0xD8E4FF, MOON = 0x9FB8FF;

    public MoonArrowForm() {
        super("race_moon_arrow", 6);
        element(Element.LIGHT);
        origin(MagicPath.NONE);
        tier(1);
    }

    @Override
    public boolean stageable() { return true; }

    @Override
    public int baseCooldown() { return 12; }

    @Override
    public float hintRange() { return (float) (SPEED * LIFE); }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        SpellStats s = ctx.formStats();
        ServerLevel level = ctx.level();
        // A lua fortalece a flecha (céu aberto à noite)
        if (level.isNight() && level.canSeeSky(caster.blockPosition())) ctx.setPowerMult(ctx.powerMult() * 1.2F);
        int n = Math.min(12, s.count());
        Vec3 start = ctx.hasOrigin() ? ctx.eyePos() : caster.getEyePosition().subtract(0, 0.15, 0);
        Vec3 look = ctx.look();
        for (int i = 0; i < n; i++) {
            float yawOff = n == 1 ? 0 : (i - (n - 1) / 2.0F) * 8.0F;
            float pitchOff = 0;
            if (s.spread) {
                yawOff = (ctx.random().nextFloat() - 0.5F) * 36.0F;
                pitchOff = (ctx.random().nextFloat() - 0.5F) * 16.0F;
            }
            Vec3 dir = CoreGlyphs.rotate(look, yawOff, pitchOff).normalize();
            new Arrow(ctx, s, start, dir).step();
        }
        // Disparo: arco de luar na mão + som de corda e cristal
        RaceFx.send(level, ParticleTypes.END_ROD, start.add(look.scale(0.6)), 8, 0.15, 0.03);
        RaceFx.ring(level, start.add(look.scale(0.4)), 0.35, 10, RaceFx.dust(SILVER, 0.7F));
        RaceFx.sound(level, start, SoundEvents.ARROW_SHOOT, 0.8F, 1.7F);
        RaceFx.sound(level, start, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
    }

    /** Uma flecha em voo. */
    private static final class Arrow {
        private final SpellContext ctx;
        private final SpellStats s;
        private Vec3 pos;
        private Vec3 dir;
        private final double speed;
        private int age;
        private int pierced;
        private int bounces;
        private final Set<UUID> hit = new HashSet<>();
        /** Magia com efeito benéfico (cura, bênção): a flecha também acerta aliados. */
        private final boolean healing;

        Arrow(SpellContext ctx, SpellStats s, Vec3 start, Vec3 dir) {
            this.ctx = ctx;
            this.s = s;
            this.pos = start;
            this.dir = dir;
            this.speed = SPEED * s.speedMult();
            this.healing = ctx.spell().effects().stream().anyMatch(e -> e.effect().isBeneficial());
        }

        void step() {
            ServerLevel level = ctx.level();
            LivingEntity caster = ctx.caster();
            if (age++ >= LIFE || caster == null || !level.isLoaded(net.minecraft.core.BlockPos.containing(pos))) {
                finish();
                return;
            }
            steer();
            Vec3 next = pos.add(dir.scale(speed));
            // Blocos
            BlockHitResult bh = null;
            if (!s.ghost) {
                bh = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
                if (bh.getType() == HitResult.Type.MISS) bh = null;
            }
            Vec3 limit = bh != null ? bh.getLocation() : next;
            // Criaturas
            EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, caster, pos, limit,
                    new AABB(pos, limit).inflate(0.6), e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator()
                            && e != caster && !hit.contains(e.getUUID()) && !ctx.ignores(e) && (healing || !ctx.isAlly(e)));
            trail(level, pos, eh != null ? eh.getLocation() : limit);
            if (eh != null) {
                Entity t = eh.getEntity();
                hit.add(t.getUUID());
                Vec3 at = RaceFx.center(t);
                ctx.impact(at, dir, t);
                ctx.resolveOnEntity(t, null);
                hitFx(level, at);
                if (pierced++ < s.pierce) {
                    pos = eh.getLocation();
                    SpellScheduler.schedule(1, this::step);
                    return;
                }
                return; // a flecha fica no alvo (o estágio seguinte, se houver, já nasceu do impacto)
            }
            if (bh != null) {
                ctx.impact(bh.getLocation(), dir, null);
                ctx.resolveOnBlock(bh, null);
                hitFx(level, bh.getLocation());
                if (bounces++ < s.ricochet) {
                    Direction face = bh.getDirection();
                    Vec3 nrm = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
                    dir = dir.subtract(nrm.scale(2 * dir.dot(nrm))).normalize();
                    pos = bh.getLocation().add(nrm.scale(0.1));
                    SpellScheduler.schedule(1, this::step);
                    return;
                }
                return;
            }
            pos = next;
            SpellScheduler.schedule(1, this::step);
        }

        /** Curva para o inimigo mais alinhado à frente (pouco por padrão; muito com Teleguiado). */
        private void steer() {
            double maxTurn = s.homing ? 0.35 : 0.08;
            double cone = s.homing ? 0.5 : 0.92;
            LivingEntity best = null;
            double bestDot = cone;
            for (LivingEntity e : ctx.level().getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(TRACK_RANGE),
                    e -> e.isAlive() && e != ctx.caster() && !hit.contains(e.getUUID()) && !ctx.isAlly(e) && !e.isSpectator())) {
                Vec3 to = RaceFx.center(e).subtract(pos);
                double len = to.length();
                if (len < 0.5 || len > TRACK_RANGE) continue;
                double dot = to.scale(1 / len).dot(dir);
                if (dot > bestDot) {
                    bestDot = dot;
                    best = e;
                }
            }
            if (best == null) return;
            Vec3 want = RaceFx.center(best).subtract(pos).normalize();
            Vec3 turned = dir.add(want.subtract(dir).scale(maxTurn));
            if (turned.lengthSqr() > 1e-4) dir = turned.normalize();
        }

        private void trail(ServerLevel level, Vec3 from, Vec3 to) {
            Vec3 d = to.subtract(from);
            int n = Math.max(2, (int) (d.length() / 0.5));
            for (int i = 0; i < n; i++) {
                Vec3 p = from.add(d.scale(i / (double) n));
                level.sendParticles(RaceFx.dust(i % 2 == 0 ? SILVER : MOON, 0.9F), p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
            }
            level.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 1, 0.03, 0.03, 0.03, 0.005);
            if (age % 3 == 0) level.sendParticles(ParticleTypes.GLOW, to.x, to.y, to.z, 1, 0.08, 0.08, 0.08, 0);
        }

        private void hitFx(ServerLevel level, Vec3 at) {
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 10, 0.15, 0.15, 0.15, 0.08);
            RaceFx.ring(level, at, 0.6, 12, RaceFx.dust(SILVER, 0.8F));
            RaceFx.sound(level, at, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.7F, 1.5F);
        }

        private void finish() {
            ctx.level().sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 4, 0.1, 0.1, 0.1, 0.02);
            ctx.end(pos, dir);
        }
    }
}
