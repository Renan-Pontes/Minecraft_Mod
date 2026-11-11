package com.renanpontes.isekaimahou.feature.character.glyph;

import java.util.Optional;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.ArenaBounds;
import com.renanpontes.isekaimahou.feature.character.RaceFx;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Salto Predador (Bestial): salta como uma fera — na direção da presa mais alinhada com o olhar (até 14 blocos, × área)
 * ou, sem presa, para a frente. Ao cair (ou ao alcançar a presa), aplica os efeitos da magia em quem estiver a até 2,5
 * blocos (× área) do ponto de pouso. Funciona sem efeito nenhum (só o salto). A queda do salto não machuca.
 * Não vira estágio 2+ (quem salta é você). Avisa {@code impact} em cada alvo do pouso e {@code end} no pouso.
 */
public class PredatorLeapForm extends FormGlyph {
    public static final float REACH = 14.0F;
    public static final float LAND_RADIUS = 2.5F;
    private static final int MAX_AIR = 40;
    private static final int AMBER = 0xD08A3A;

    public PredatorLeapForm() {
        super("race_predator_leap", 6);
        element(Element.WIND);
        origin(MagicPath.NONE);
        tier(1);
    }

    @Override
    public boolean requiresEffect() { return false; }

    @Override
    public int baseCooldown() { return 30; }

    @Override
    public float copies(SpellStats stats, int circle) { return 1F; }

    @Override
    public float hintRange() { return REACH; }

    @Override
    public float hintRadius() { return LAND_RADIUS; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null || !caster.isAlive() || caster.isPassenger()) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        float reach = Math.min(24F, s.radius(REACH));
        Vec3 eye = caster.getEyePosition();
        Vec3 look = ctx.look();

        // Presa: o inimigo mais alinhado com o olhar, à vista
        LivingEntity prey = null;
        double best = 0.88;
        for (LivingEntity e : ctx.enemiesAround(caster.position(), reach)) {
            if (e.isSpectator()) continue;
            Vec3 to = RaceFx.center(e).subtract(eye);
            double len = to.length();
            if (len < 1.5) continue;
            double dot = to.scale(1 / len).dot(look);
            if (dot <= best) continue;
            if (level.clip(new ClipContext(eye, RaceFx.center(e), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster))
                    .getType() == HitResult.Type.BLOCK) continue;
            best = dot;
            prey = e;
        }
        Vec3 vel;
        if (prey != null) {
            Vec3 to = prey.position().subtract(caster.position());
            Vec3 flat = new Vec3(to.x, 0, to.z);
            double dist = flat.length();
            double h = Math.min(1.7, 0.45 + dist * 0.09);
            vel = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0.6, 0) : flat.normalize().scale(h).add(0, 0.5 + Math.max(0, Math.min(0.5, to.y * 0.08)), 0);
        } else {
            Vec3 flat = new Vec3(look.x, 0, look.z);
            flat = flat.lengthSqr() < 1e-4 ? Vec3.ZERO : flat.normalize();
            double h = 1.1 * Math.min(1.5, reach / REACH);
            vel = flat.scale(h).add(0, 0.55 + Math.max(0, look.y) * 0.4, 0);
        }
        caster.setDeltaMovement(vel);
        caster.hurtMarked = true;
        caster.resetFallDistance();

        // Impulso: poeira do chão + rosnado
        BlockState ground = level.getBlockState(caster.blockPosition().below());
        if (!ground.isAir()) {
            RaceFx.send(level, new BlockParticleOption(ParticleTypes.BLOCK, ground), caster.position(), 20, 0.4, 0.15);
        }
        level.sendParticles(ParticleTypes.CLOUD, caster.getX(), caster.getY() + 0.1, caster.getZ(), 10, 0.3, 0.05, 0.3, 0.05);
        RaceFx.sound(level, caster.position(), SoundEvents.FOX_AGGRO, 0.9F, 0.9F);
        RaceFx.sound(level, caster.position(), SoundEvents.GOAT_LONG_JUMP, 0.8F, 1.1F);

        Optional<ArenaBounds.Arena> arena = ArenaBounds.at(level, caster.position());
        final LivingEntity target = prey;
        SpellScheduler.schedule(2, () -> fly(ctx, caster, target, arena.orElse(null), 2));
    }

    /** Acompanha o voo até pousar (ou tocar a presa), no máx. {@value #MAX_AIR} ticks. */
    private static void fly(SpellContext ctx, LivingEntity caster, LivingEntity prey, ArenaBounds.Arena arena, int age) {
        if (!caster.isAlive() || caster.level() != ctx.level()) return;
        caster.resetFallDistance();
        ServerLevel level = ctx.level();
        // Rastro âmbar no ar
        level.sendParticles(RaceFx.dust(AMBER, 0.9F), caster.getX(), caster.getY() + 0.6, caster.getZ(), 2, 0.2, 0.2, 0.2, 0);
        boolean reached = prey != null && prey.isAlive() && caster.distanceToSqr(prey) < 2.6 * 2.6;
        if ((caster.onGround() && age > 3) || reached || age >= MAX_AIR || caster.isInWater()
                || (arena != null && !arena.contains(caster.position()))) {
            land(ctx, caster);
            return;
        }
        SpellScheduler.schedule(1, () -> fly(ctx, caster, prey, arena, age + 1));
    }

    private static void land(SpellContext ctx, LivingEntity caster) {
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        float r = s.radius(LAND_RADIUS);
        Vec3 at = caster.position();
        caster.resetFallDistance();
        int hits = 0;
        if (!ctx.spell().effects().isEmpty()) {
            for (LivingEntity e : ctx.enemiesAround(at.add(0, 0.5, 0), r + 0.5)) {
                if (++hits > 16) break;
                Vec3 c = RaceFx.center(e);
                Vec3 out = c.subtract(at);
                ctx.impact(c, out.lengthSqr() > 1e-4 ? out.normalize() : ctx.look(), e);
                ctx.resolveOnEntity(e, null);
                level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 8, 0.3, 0.3, 0.3, 0.2);
            }
        }
        // Pouso: anel de impacto + lascas do chão
        BlockState ground = level.getBlockState(BlockPos.containing(at).below());
        if (!ground.isAir()) {
            RaceFx.ring(level, at.add(0, 0.1, 0), r, 20, new BlockParticleOption(ParticleTypes.BLOCK, ground));
        }
        RaceFx.ring(level, at.add(0, 0.15, 0), r * 0.7, 14, RaceFx.dust(AMBER, 1.2F));
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y + 0.6, at.z, hits > 0 ? 2 : 0, 0.4, 0.1, 0.4, 0);
        RaceFx.sound(level, at, SoundEvents.RAVAGER_STEP, 1.0F, 1.3F);
        if (hits > 0) RaceFx.sound(level, at, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9F, 0.8F);
        ctx.end(at.add(0, 0.5, 0), ctx.look());
    }
}
