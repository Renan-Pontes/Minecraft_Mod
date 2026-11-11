package com.renanpontes.isekaimahou.feature.fusions_b.glyph;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.fusions_b.FbFx;
import com.renanpontes.isekaimahou.feature.fusions_b.FbRegistries;
import com.renanpontes.isekaimahou.feature.fusions_b.FbTasks;
import com.renanpontes.isekaimahou.feature.fusions_b.FbUtil;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.HellfireImpEntity;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.InfernalChainEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Glifos de CHAMA INFERNAL (Fogo + Sombra).
 * Tier 1: Fogo Negro · Tier 2: Corrente Infernal · Tier 3: Pacto de Sangue · Tier 4: Portal Infernal.
 */
public final class HellfireGlyphs {

    public static void register() {
        GlyphRegistry.register(new BlackFlame());
        GlyphRegistry.register(new InfernalChain());
        GlyphRegistry.register(new BloodPact());
        GlyphRegistry.register(new HellPortal());
    }

    /**
     * Fogo Negro: dano 2 e chamas negras por 5s que água e leite não apagam. A cada segundo queimam 1,5
     * (+1 por nível) e metade do dano volta como vida para quem conjurou.
     */
    public static class BlackFlame extends EffectGlyph {
        public BlackFlame() {
            super("black_flame", 12);
            element(Element.HELLFIRE);
            tier(1);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            ServerLevel sl = ctx.level();
            for (LivingEntity e : FbUtil.offensiveTargets(ctx, target, stats.radius(2.5F))) {
                FbUtil.hurt(ctx, e, 2.0F, stats, null);
                if (!e.isAlive()) continue;
                e.addEffect(new MobEffectInstance(FbRegistries.BLACK_FLAME, stats.duration(100), FbUtil.amp(ctx, stats)), ctx.caster());
                Vec3 c = FbUtil.center(e);
                sl.sendParticles(FbFx.fade(FbFx.HELLFIRE, FbFx.HELL_BLACK, 1.6F), c.x, c.y, c.z, 20,
                        e.getBbWidth() * 0.5, e.getBbHeight() * 0.4, e.getBbWidth() * 0.5, 0.03);
                sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y, c.z, 6, e.getBbWidth() * 0.4, e.getBbHeight() * 0.3,
                        e.getBbWidth() * 0.4, 0.02);
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 4, 0.2, 0.3, 0.2, 0.02);
            }
            FbFx.sound(sl, target.position(), SoundEvents.FIRECHARGE_USE, 0.9F, 0.5F);
            FbFx.sound(sl, target.position(), SoundEvents.SOUL_ESCAPE.value(), 1.2F, 0.7F);
        }
    }

    /**
     * Corrente Infernal: três correntes em brasa saem de um selo no chão e prendem o alvo por 4s (Enraizado).
     * Enquanto preso ele queima e leva dano de chama infernal a cada segundo. Em Si Mesmo: acorrenta os
     * 3 inimigos mais próximos.
     */
    public static class InfernalChain extends EffectGlyph {
        public InfernalChain() {
            super("infernal_chain", 16);
            element(Element.HELLFIRE);
            tier(2);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            List<LivingEntity> targets = new ArrayList<>();
            if (FbUtil.isSelf(ctx, target)) {
                List<LivingEntity> around = ctx.enemiesAround(target.position(), stats.radius(8F));
                around.sort((a, b) -> Double.compare(a.distanceToSqr(target), b.distanceToSqr(target)));
                for (int i = 0; i < Math.min(3, around.size()); i++) targets.add(around.get(i));
            } else if (target instanceof LivingEntity le) {
                targets.add(le);
            }
            float tickDmg = 1.5F * stats.power();
            for (LivingEntity e : targets) {
                if (FbUtil.untouchable(e)) continue;
                int ticks = stats.duration(80);
                if (FbUtil.isBoss(e)) ticks = Math.max(10, ticks / 2);
                FbUtil.hurt(ctx, e, 2.0F, stats, null);
                if (!e.isAlive()) continue;
                e.igniteForSeconds(2F);
                InfernalChainEntity.bind(ctx, e, ticks, tickDmg, stats);
            }
        }
    }

    /**
     * Pacto de Sangue: quem conjura sacrifica 30% da vida atual (nunca abaixo de meio coração)
     * e o alvo ganha poder por 20s: +dano corpo a corpo, +15% de poder mágico e +velocidade por nível. Quanto
     * mais sangue, mais forte o pacto. Invertido em inimigos: rouba o sangue deles para fortalecer você.
     */
    public static class BloodPact extends EffectGlyph {
        public BloodPact() {
            super("blood_pact", 10);
            element(Element.HELLFIRE);
            tier(3);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le) || !FbUtil.firstHit(ctx, "blood_pact", le)) return;
            ServerLevel sl = ctx.level();
            LivingEntity caster = ctx.caster();
            int duration = stats.duration(400);
            if (caster != null && le != caster && !ctx.isAlly(le)) {
                // Pacto invertido: o sangue do inimigo alimenta o conjurador
                FbUtil.hurt(ctx, le, 4.0F, stats, null);
                caster.addEffect(new MobEffectInstance(FbRegistries.BLOOD_PACT, duration / 2, 0), caster);
                FbFx.line(sl, FbUtil.center(le), FbUtil.center(caster), FbFx.dust(FbFx.BLOOD, 1.2F), 3.0);
                FbFx.sound(sl, caster.position(), SoundEvents.WARDEN_HEARTBEAT, 1.5F, 1.2F);
                return;
            }
            LivingEntity payer = caster != null ? caster : le;
            float hp = payer.getHealth();
            if (hp <= 2.5F) {
                if (payer instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.translatable("message.isekai_mahou.blood_pact.too_weak"), true);
                }
                FbFx.sound(sl, payer.position(), SoundEvents.FIRE_EXTINGUISH, 0.6F, 0.8F);
                return;
            }
            float cost = Math.min(hp - 1.0F, Math.max(2.0F, hp * 0.3F));
            payer.setHealth(hp - cost);
            int amp = Mth.clamp((int) (cost / 4F) + FbUtil.amp(ctx, stats), 0, 3);
            le.addEffect(new MobEffectInstance(FbRegistries.BLOOD_PACT, duration, amp), caster);
            Vec3 pc = FbUtil.center(payer);
            sl.sendParticles(FbFx.dust(FbFx.BLOOD, 1.5F), pc.x, pc.y, pc.z, 30, payer.getBbWidth() * 0.5, payer.getBbHeight() * 0.4,
                    payer.getBbWidth() * 0.5, 0.0);
            sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, pc.x, pc.y, pc.z, (int) Math.ceil(cost), 0.3, 0.3, 0.3, 0.1);
            if (payer != le) FbFx.line(sl, pc, FbUtil.center(le), FbFx.dust(FbFx.BLOOD, 1.0F), 3.0);
            FbFx.ring(sl, le.position().add(0, 0.1, 0), FbFx.fade(FbFx.HELLFIRE, FbFx.HELL_BLACK, 1.3F), le.getBbWidth() + 0.7, 20, 0);
            FbFx.sound(sl, pc, SoundEvents.PLAYER_HURT, 0.8F, 0.8F);
            FbFx.sound(sl, pc, SoundEvents.WARDEN_HEARTBEAT, 2.0F, 1.0F);
        }
    }

    /**
     * Portal Infernal: abre uma fenda de fogo no chão (no alvo, no bloco atingido, ou à sua frente em Si Mesmo)
     * de onde saem 2 diabretes aliados (+1 por Dividir, máx. 4) que lutam por 20s e somem em cinzas.
     */
    public static class HellPortal extends EffectGlyph {
        private static final String KEY = "hell_portal";

        public HellPortal() {
            super("hell_portal", 35);
            element(Element.HELLFIRE);
            tier(4);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (ctx.caster() == null || !FbUtil.once(ctx, KEY)) return;
            Vec3 at = FbUtil.isSelf(ctx, target)
                    ? target.position().add(FbUtil.flatLook(ctx).scale(2.5)).add(0, 1, 0)
                    : target.position().add(0, 0.5, 0);
            open(ctx, FbUtil.ground(ctx.level(), at, 6), stats);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (ctx.caster() == null || FbUtil.isSelfFeetEcho(ctx, hit, KEY) || !FbUtil.once(ctx, KEY)) return;
            Vec3 at = Vec3.atBottomCenterOf(hit.getBlockPos().above());
            open(ctx, FbUtil.ground(ctx.level(), at, 6), stats);
        }

        private static void open(SpellContext ctx, Vec3 at, SpellStats stats) {
            int count = Math.min(HellfireImpEntity.MAX_PER_OWNER, 2 + Math.max(0, stats.split));
            FbTasks.add(new PortalTask(ctx, at, count, stats.duration(400), FbUtil.power(ctx, stats)));
        }

        /** Animação da fenda: abre, cospe os diabretes um a um e fecha. */
        static final class PortalTask implements FbTasks.Task {
            private static final int OPEN_TICKS = 50;
            private final SpellContext ctx;
            private final ServerLevel level;
            private final Vec3 at;
            private final int count;
            private final int life;
            private final float power;
            private int age;
            private int spawned;

            PortalTask(SpellContext ctx, Vec3 at, int count, int life, float power) {
                this.ctx = ctx;
                this.level = ctx.level();
                this.at = at;
                this.count = count;
                this.life = life;
                this.power = power;
            }

            @Override
            public boolean tick() {
                age++;
                if (age == 1) {
                    FbFx.sound(level, at, SoundEvents.EVOKER_PREPARE_SUMMON, 1.2F, 0.6F);
                    FbFx.sound(level, at, SoundEvents.BLAZE_AMBIENT, 1.0F, 0.5F);
                }
                double r = Math.min(1.6, age * 0.06);
                if (age % 2 == 0) {
                    FbFx.ring(level, at.add(0, 0.1, 0), ParticleTypes.FLAME, r, 18, 0.01);
                    FbFx.ring(level, at.add(0, 0.12, 0), FbFx.fade(FbFx.HELLFIRE, FbFx.HELL_BLACK, 1.4F), r * 0.7, 14, 0);
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 0.2, at.z, 3, r * 0.4, 0.05, r * 0.4, 0.06);
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.3, at.z, 2, r * 0.4, 0.1, r * 0.4, 0.04);
                }
                if (age % 5 == 0) level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.2, at.z, 2, r * 0.5, 0.05, r * 0.5, 0);
                LivingEntity owner = ctx.caster();
                if (spawned < count && age >= 15 + spawned * 7 && owner != null && owner.isAlive() && owner.level() == level) {
                    HellfireImpEntity.emerge(level, owner, at, life, power);
                    spawned++;
                    level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.5, at.z, 20, 0.3, 0.4, 0.3, 0.08);
                    FbFx.sound(level, at, SoundEvents.BLAZE_SHOOT, 1.0F, 0.7F);
                }
                if (age >= OPEN_TICKS) {
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.3, at.z, 20, 0.8, 0.2, 0.8, 0.03);
                    FbFx.sound(level, at, SoundEvents.FIRE_EXTINGUISH, 1.0F, 0.6F);
                    return false;
                }
                return true;
            }
        }
    }

    private HellfireGlyphs() {}
}
