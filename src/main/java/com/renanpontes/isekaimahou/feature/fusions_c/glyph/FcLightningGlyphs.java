package com.renanpontes.isekaimahou.feature.fusions_c.glyph;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.SpellStack;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.fusions_c.FcFx;
import com.renanpontes.isekaimahou.feature.fusions_c.FcNet;
import com.renanpontes.isekaimahou.feature.fusions_c.FcTasks;
import com.renanpontes.isekaimahou.feature.fusions_c.FcUtil;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.BallLightningEntity;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.FusionBeamEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Glifos de RAIO das Misturas C: Canhão Elétrico (tier 4) e Bola de Raio (tier 3). */
public final class FcLightningGlyphs {

    public static void register() {
        GlyphRegistry.register(new Railgun());
        GlyphRegistry.register(new BallLightning());
    }

    /**
     * Canhão Elétrico: trilhos de eletricidade se alinham na sua frente por 0,8 s (você ainda pode mirar; é uma
     * conjuração pendente que pode ser contrariada) e então um projétil a uma fração da velocidade da luz atravessa TUDO
     * em linha reta por 64 blocos: dano 18 a todos no caminho, Eletrizado 2 e um TÚNEL apagado no terreno.
     */
    public static class Railgun extends EffectGlyph {
        public static final int CHARGE = 16;
        public static final float RANGE = 64F;

        public Railgun() {
            super("railgun", 36);
            element(Element.LIGHTNING);
            tier(4);
            minCircle(5);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!FcUtil.once(ctx, "railgun")) return;
            // Mira: o olhar (Si Mesmo/estágio) ou o alvo atingido; enquanto carrega, a mira acompanha o conjurador
            charge(ctx, stats, FcUtil.isSelf(ctx, target) || ctx.hasOrigin() ? null : target, null);
        }

        /** Projétil/Toque num bloco: dispara de você na direção do ponto atingido (o "eco" do Si Mesmo já foi). */
        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (!FcUtil.once(ctx, "railgun")) return;
            charge(ctx, stats, null, ctx.hasOrigin() ? null : hit.getLocation());
        }

        private static void charge(SpellContext ctx, SpellStats stats, Entity aimEntity, Vec3 aimPoint) {
            ServerLevel sl = ctx.level();
            LivingEntity caster = ctx.caster();
            final Entity aimAt = aimEntity;
            final boolean follow = caster != null && !ctx.hasOrigin();
            Vec3[] origin = {ctx.eyePos().add(0, -0.2, 0)};
            Vec3 first = aimAt != null ? FcUtil.center(aimAt).subtract(origin[0])
                    : aimPoint != null ? aimPoint.subtract(origin[0]) : ctx.look();
            if (first.lengthSqr() < 1.0E-4) first = ctx.look();
            Vec3[] dir = {first.normalize()};
            boolean[] countered = {false};
            SpellStack.Pending pending = caster != null && caster.isAlive()
                    ? SpellStack.push(sl, caster, origin[0], 2F, CHARGE, SpellStack.Tier.MAJOR,
                    Component.translatable("pending.isekai_mahou.railgun"), Element.LIGHTNING, () -> countered[0] = true)
                    : null;
            int[] t = {0};
            FcFx.sound(sl, origin[0], SoundEvents.BEACON_ACTIVATE, 1.5F, 1.6F);
            FcTasks.add(() -> {
                if (countered[0]) return false;
                if (caster != null && (!caster.isAlive() || caster.isRemoved() || caster.level() != sl)) {
                    if (pending != null) SpellStack.cancel(pending);
                    return false;
                }
                // Congelado/Petrificado no meio da carga: os trilhos se desfazem (a conjuração foi interrompida)
                if (caster != null && Statuses.incapacitated(caster)) {
                    if (pending != null) SpellStack.cancel(pending);
                    sl.sendParticles(ParticleTypes.SMOKE, origin[0].x, origin[0].y, origin[0].z, 12, 0.3, 0.3, 0.3, 0.02);
                    FcFx.sound(sl, origin[0], SoundEvents.BEACON_DEACTIVATE, 1F, 1.4F);
                    return false;
                }
                if (follow) {
                    origin[0] = caster.getEyePosition().add(0, -0.2, 0);
                    Vec3 want = aimAt != null && aimAt.isAlive() ? FcUtil.center(aimAt).subtract(origin[0]) : caster.getLookAngle();
                    if (caster instanceof Mob m && m.getTarget() != null) want = FcUtil.center(m.getTarget()).subtract(origin[0]);
                    if (want.lengthSqr() > 1.0E-4) dir[0] = want.normalize();
                    caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 2, false, false));
                }
                int age = t[0]++;
                if (age < CHARGE) {
                    rails(sl, origin[0], dir[0], age);
                    if (age % 4 == 0) FcFx.sound(sl, origin[0], SoundEvents.BEACON_POWER_SELECT, 1F, 0.8F + age / (float) CHARGE);
                    return true;
                }
                if (pending != null) SpellStack.resolve(pending);
                fire(ctx, stats, sl, origin[0], dir[0]);
                return false;
            });
        }

        /** Dois trilhos de eletricidade paralelos na frente do conjurador, cada vez mais brilhantes. */
        private static void rails(ServerLevel sl, Vec3 o, Vec3 d, int age) {
            Vec3 side = d.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize().scale(0.45);
            float len = 1.5F + 3F * age / CHARGE;
            for (int s = -1; s <= 1; s += 2) {
                Vec3 a = o.add(side.scale(s)).add(d.scale(0.6));
                FcFx.line(sl, a, a.add(d.scale(len)), FcFx.dust(FcFx.VOLT, 0.8F), 2.5);
            }
            Vec3 tip = o.add(d.scale(0.8 + len));
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z, 3, 0.15, 0.15, 0.15, 0.2);
            if (age > CHARGE - 5) sl.sendParticles(ParticleTypes.END_ROD, tip.x, tip.y, tip.z, 2, 0.05, 0.05, 0.05, 0.02);
        }

        private static void fire(SpellContext ctx, SpellStats stats, ServerLevel sl, Vec3 from, Vec3 dir) {
            LivingEntity caster = ctx.caster();
            float range = RANGE * (1F + 0.25F * stats.aoe);
            Vec3 to = from.add(dir.scale(range));
            float width = 1.3F + 0.3F * stats.aoe;
            // Tudo na linha: o projétil não para em nada
            List<LivingEntity> hits = new ArrayList<>();
            for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(width + 1),
                    le -> le.isAlive() && le != caster && !ctx.isAlly(le) && !le.isSpectator())) {
                Vec3 c = FcUtil.center(le);
                double tt = c.subtract(from).dot(dir);
                if (tt < 0 || tt > range) continue;
                if (c.subtract(from.add(dir.scale(tt))).length() <= width + le.getBbWidth() * 0.5) hits.add(le);
            }
            for (LivingEntity le : hits) {
                le.invulnerableTime = 0;
                ctx.damage(le, 18F, stats, null, Element.LIGHTNING);
                if (!le.isAlive()) continue;
                Statuses.applyUpTo(le, Statuses.Kind.SHOCKED, 2, 120, caster, ctx.powerMult(), 2);
                FcUtil.knock(le, dir.scale(0.9).add(0, 0.2, 0));
                Vec3 c = FcUtil.center(le);
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 20, 0.3, 0.4, 0.3, 0.6);
            }
            // Túnel no terreno (respeita a regra de destruição)
            Destruction.line(sl, from.add(dir.scale(1.5)), to, width, width, caster, Destruction.Mode.ERASE);
            FusionBeamEntity.visual(sl, from, to, FusionBeamEntity.Kind.RAILGUN, 0.55F, 18);
            FcFx.line(sl, from, from.add(dir.scale(Math.min(range, 40))), ParticleTypes.ELECTRIC_SPARK, 0.8);
            sl.sendParticles(ParticleTypes.FLASH, from.x, from.y, from.z, 1, 0, 0, 0, 0);
            if (caster != null && !FcUtil.bossLike(caster) && !caster.isShiftKeyDown()) FcUtil.addMotion(caster, dir.scale(-0.45));
            FcFx.sound(sl, from, SoundEvents.WARDEN_SONIC_BOOM, 2.5F, 1.4F);
            FcFx.sound(sl, from, SoundEvents.TRIDENT_THUNDER.value(), 2F, 1.2F);
            FcFx.sound(sl, from.add(dir.scale(range * 0.5)), SoundEvents.LIGHTNING_BOLT_THUNDER, 2F, 1.5F);
            BossKit.shake(sl, from, 32, 1.4F, 14);
            FcNet.flash(sl, from, 28, FcFx.VOLT_WHITE, 0.55F, 10);
        }
    }

    /**
     * Bola de Raio: um orbe de plasma flutua devagar (puxado para o inimigo mais próximo) por 8 s e a cada 0,6 s
     * dispara raios em até 3 inimigos a 7 blocos (dano 4,5 e Eletrizado — ELETROCUTA quem está Molhado). Estoura num
     * clarão ao bater num bloco ou no fim. Dividir: +1 alvo por raio.
     */
    public static class BallLightning extends EffectGlyph {
        public BallLightning() {
            super("ball_lightning", 22);
            element(Element.LIGHTNING);
            tier(3);
            minCircle(3);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!FcUtil.once(ctx, "ball_lightning")) return;
            if (FcUtil.isSelf(ctx, target)) {
                Vec3 at = ctx.eyePos().add(ctx.look().scale(1.4));
                BallLightningEntity.spawn(ctx, stats, at, ctx.look());
            } else {
                BallLightningEntity.spawn(ctx, stats, FcUtil.center(target).add(0, target.getBbHeight() * 0.5 + 1.2, 0), ctx.look());
            }
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (!FcUtil.once(ctx, "ball_lightning")) return;
            BallLightningEntity.spawn(ctx, stats, FcUtil.onTop(hit).add(0, 1.5, 0), ctx.look());
        }
    }

    private FcLightningGlyphs() {}
}
