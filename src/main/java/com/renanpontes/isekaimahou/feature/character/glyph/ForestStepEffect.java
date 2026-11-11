package com.renanpontes.isekaimahou.feature.character.glyph;

import java.util.Optional;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.ArenaBounds;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.character.RaceFx;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Passo da Floresta (Elfo): em você (Si Mesmo), some num redemoinho de folhas e reaparece até 8 blocos à frente
 * (× área), fica rápido e os inimigos perdem o seu rastro. Em aliados: velocidade e um pouco de regeneração. Com
 * Inverter num inimigo: raízes da floresta o prendem (Enraizado).
 * <p>
 * O salto nunca atravessa paredes (vai até o último ponto livre) nem sai de uma arena ({@link ArenaBounds}).
 */
public class ForestStepEffect extends RaceEffectGlyph {
    public static final float BLINK = 8.0F;
    public static final int BUFF_TICKS = 100;
    private static final int LEAF = 0x5FC46F;

    public ForestStepEffect() {
        super("race_forest_step", 8, Element.WIND, 2);
    }

    @Override
    public boolean isBeneficial() { return true; }

    @Override
    public int hintDuration() { return BUFF_TICKS; }

    @Override
    public float hintRadius() { return -1F; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        ServerLevel level = ctx.level();
        if (le != ctx.caster() && !ctx.isAlly(le)) {
            Statuses.apply(le, Statuses.Kind.ROOTED, 1, stats.duration(60), ctx.caster(), stats.power());
            leaves(level, le.position().add(0, 0.2, 0), 18);
            RaceFx.sound(level, le.position(), SoundEvents.ROOTS_PLACE, 0.9F, 0.8F);
            return;
        }
        if (le == ctx.caster()) blink(ctx, le, stats);
        le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, stats.duration(BUFF_TICKS), stats.power() >= 1.8F ? 2 : 1,
                false, true, true), ctx.caster());
        if (le != ctx.caster()) {
            le.addEffect(new MobEffectInstance(MobEffects.REGENERATION, stats.duration(60), 0, false, true, true), ctx.caster());
        }
        leaves(level, le.position().add(0, 1.0, 0), 14);
        RaceFx.sound(level, le.position(), SoundEvents.AZALEA_LEAVES_BREAK, 0.8F, 1.2F);
    }

    /** Teleporte curto na direção do olhar, até o último ponto livre (e dentro da arena, se estiver numa). */
    private static void blink(SpellContext ctx, LivingEntity le, SpellStats stats) {
        ServerLevel level = ctx.level();
        Vec3 look = ctx.look();
        Vec3 flat = new Vec3(look.x, Math.max(-0.3, Math.min(0.5, look.y)), look.z);
        if (flat.lengthSqr() < 1e-4) return;
        flat = flat.normalize();
        float dist = Math.min(16F, Math.max(4F, stats.radius(BLINK)));
        Vec3 from = le.position();
        Optional<ArenaBounds.Arena> arena = ArenaBounds.at(level, from);
        Vec3 best = null;
        for (double d = 0.5; d <= dist + 1e-3; d += 0.5) {
            Vec3 p = from.add(flat.scale(d));
            AABB box = le.getBoundingBox().move(p.subtract(from));
            if (!level.noCollision(le, box)) {
                // Degrau: tenta meio bloco acima antes de desistir
                Vec3 up = p.add(0, 1.0, 0);
                if (level.noCollision(le, le.getBoundingBox().move(up.subtract(from)))) p = up;
                else break;
            }
            if (arena.isPresent() && !arena.get().contains(p)) break;
            if (!level.isLoaded(net.minecraft.core.BlockPos.containing(p))) break;
            best = p;
        }
        if (best == null || best.distanceToSqr(from) < 1.0) return;
        leaves(level, from.add(0, 1.0, 0), 24);
        RaceFx.line(level, from.add(0, 1.0, 0), best.add(0, 1.0, 0), RaceFx.dust(LEAF, 0.8F));
        if (le instanceof ServerPlayer sp) sp.teleportTo(best.x, best.y, best.z);
        else le.teleportTo(best.x, best.y, best.z);
        le.fallDistance = 0;
        le.resetFallDistance();
        leaves(level, best.add(0, 1.0, 0), 24);
        RaceFx.send(level, ParticleTypes.CHERRY_LEAVES, best.add(0, 1.4, 0), 10, 0.5, 0.02);
        RaceFx.sound(level, best, SoundEvents.ENDERMAN_TELEPORT, 0.5F, 1.8F);
        // Os inimigos perdem o rastro (só criaturas comuns: chefes não se enganam)
        for (Mob m : level.getEntitiesOfClass(Mob.class, le.getBoundingBox().inflate(16), m -> m.getTarget() == le)) {
            if (!Statuses.isBossLike(m)) m.setTarget(null);
        }
    }

    private static void leaves(ServerLevel level, Vec3 at, int count) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LEAVES.defaultBlockState()),
                at.x, at.y, at.z, count, 0.4, 0.6, 0.4, 0.05);
        level.sendParticles(RaceFx.dust(LEAF, 1.0F), at.x, at.y, at.z, count / 2, 0.5, 0.6, 0.5, 0.02);
    }
}
