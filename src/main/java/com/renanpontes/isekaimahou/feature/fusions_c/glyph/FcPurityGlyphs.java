package com.renanpontes.isekaimahou.feature.fusions_c.glyph;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.fusions_c.FcFx;
import com.renanpontes.isekaimahou.feature.fusions_c.FcUtil;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.FusionBeamEntity;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.FusionShockwaveEntity;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.SacredSpringEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Glifos de PUREZA das Misturas C: Fonte Sagrada (tier 3) e Purgar (tier 4). Ambos de suporte (aliados). */
public final class FcPurityGlyphs {

    public static void register() {
        GlyphRegistry.register(new SacredSpring());
        GlyphRegistry.register(new Purge());
    }

    /**
     * Fonte Sagrada: uma fonte de mármore com um lótus de luz brota do chão por 12 s. A cada segundo cura os aliados no
     * raio 5 e tira deles a PIOR condição ruim; inimigos ficam Molhados e mortos-vivos queimam na água benta. Mire no
     * chão (ou em si mesmo / num aliado) para erguê-la.
     */
    public static class SacredSpring extends EffectGlyph {
        public SacredSpring() {
            super("sacred_spring", 28);
            element(Element.PURITY);
            tier(3);
            minCircle(4);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!FcUtil.once(ctx, "sacred_spring")) return;
            SacredSpringEntity.spawn(ctx, stats, FcUtil.ground(ctx.level(), target.position(), 8));
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (!FcUtil.once(ctx, "sacred_spring")) return;
            SacredSpringEntity.spawn(ctx, stats, FcUtil.onTop(hit));
        }
    }

    /**
     * Purgar: arranca TODAS as condições e efeitos ruins do aliado (e dos aliados a até 4 blocos dele) e os ARREMESSA
     * nos inimigos próximos como raios de luz: cada inimigo (até 5, a 8 blocos) leva dano 4 + 3 por condição tirada
     * (máx. 40) e RECEBE as condições purgadas. Os purificados recuperam um pouco de vida.
     */
    public static class Purge extends EffectGlyph {
        public Purge() {
            super("purge", 30);
            element(Element.PURITY);
            tier(4);
            minCircle(5);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity ally) || !FcUtil.once(ctx, FcUtil.posKey("purge", target.position()))) return;
            ServerLevel sl = ctx.level();
            Vec3 c = FcUtil.center(ally);
            // O aliado alvo e os aliados colados nele
            List<LivingEntity> allies = new ArrayList<>();
            allies.add(ally);
            for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(4),
                    le -> le != ally && le.isAlive() && ctx.isAlly(le) && le.distanceToSqr(c) <= 16)) {
                if (FcUtil.once(ctx, "purge@" + le.getUUID())) allies.add(le);
            }
            List<FcCleansing.Removed> removed = new ArrayList<>();
            for (LivingEntity a : allies) {
                List<FcCleansing.Removed> r = FcCleansing.purgeAll(a);
                removed.addAll(r);
                ctx.heal(a, 2F + 0.5F * r.size(), stats);
                Vec3 ac = FcUtil.center(a);
                // A "mancha" sai do corpo
                sl.sendParticles(Vfx.ink(), ac.x, ac.y, ac.z, 6 + 4 * r.size(), 0.3, 0.4, 0.3, 0.08);
                sl.sendParticles(Vfx.endRod(), ac.x, ac.y, ac.z, 12, 0.3, 0.5, 0.3, 0.08);
            }
            int weight = 0;
            for (FcCleansing.Removed r : removed) weight += r.weight();
            float pool = Math.min(40F, 4F + 3F * weight);

            List<LivingEntity> foes = FcUtil.nearestEnemies(ctx, c, stats.radius(8F), 5 + Math.max(0, stats.split));
            for (int i = 0; i < foes.size(); i++) {
                LivingEntity foe = foes.get(i);
                foe.invulnerableTime = 0;
                ctx.damage(foe, FcUtil.undead(foe) ? pool * 1.25F : pool, stats, null, Element.PURITY);
                FusionBeamEntity.visual(sl, c, FcUtil.center(foe), FusionBeamEntity.Kind.PURGE_RAY, 0.22F, 12);
            }
            // As condições purgadas são entregues aos inimigos (uma a uma, em rodízio)
            if (!foes.isEmpty()) {
                for (int i = 0; i < removed.size(); i++) {
                    LivingEntity foe = foes.get(i % foes.size());
                    if (!foe.isAlive()) continue;
                    FcCleansing.Removed r = removed.get(i);
                    if (r.kind() != null) {
                        Statuses.apply(foe, r.kind(), r.stacks(), 120, ctx.caster(), ctx.powerMult());
                    } else {
                        MobEffectInstance v = r.vanilla();
                        foe.addEffect(new MobEffectInstance(v.getEffect(), Mth.clamp(v.getDuration(), 60, 400), v.getAmplifier()), ctx.caster());
                    }
                }
            }
            FusionShockwaveEntity.visual(sl, ally.position().add(0, 0.1, 0), FusionShockwaveEntity.Kind.HOLY, 4F + Math.min(4F, weight * 0.5F), 12);
            FcFx.sphere(sl, c, FcFx.dust(FcFx.HOLY, 1.2F), 1.4, 24, 0.2);
            FcFx.sound(sl, c, SoundEvents.BELL_RESONATE, 1.4F, 1.4F);
            FcFx.sound(sl, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.6F, 1.2F);
            if (weight > 0) FcFx.sound(sl, c, SoundEvents.BEACON_POWER_SELECT, 1.2F, 1.6F);
        }
    }

    private FcPurityGlyphs() {}
}
