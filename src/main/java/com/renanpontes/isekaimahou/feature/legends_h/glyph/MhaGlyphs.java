package com.renanpontes.isekaimahou.feature.legends_h.glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.legends_h.Augments;
import com.renanpontes.isekaimahou.feature.legends_h.LhIds;
import com.renanpontes.isekaimahou.feature.legends_h.LhUtil;
import com.renanpontes.isekaimahou.feature.legends_h.mha.FullCowl;
import com.renanpontes.isekaimahou.glyph.AugmentGlyph;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Glifos comuns inspirados em My Hero Academia (homenagens ao One For All do Deku): Full Cowl, o aumento One For All,
 * Delaware Smash, Detroit Smash e Blackwhip.
 */
public final class MhaGlyphs {
    public static final int GREEN = FullCowl.GREEN;
    public static final int AIR = 0xE8F4FF;
    public static final int WHIP = 0x1E2A22;

    public static List<Glyph> all() {
        return List.of(new FullCowlEffect(), oneForAll(), new DelawareSmash(), new DetroitSmash(), new Blackwhip());
    }

    // =====================================================================================
    //  Full Cowl
    // =====================================================================================
    /**
     * EFEITO (Arcano, tier 3, C3, benéfico). O One For All corre pelo corpo inteiro por 30 s (Estender +): velocidade,
     * força, pulo e queda segura, mais fortes quanto maior a porcentagem. A porcentagem é a SEGURA do seu Círculo
     * (C1–3 5%, C4 8%, C5 15%, C6 20%, C7 30%, C8+ 45%); cada Amplificar sobe um degrau (5→8→15→20→30→45→60→80→100%).
     * Acima do seguro, o corpo se quebra aos poucos (dano a cada 2 s, nunca mata; com 25% de vida volta ao seguro e
     * fica com o braço quebrado). Ver {@link FullCowl}.
     */
    public static class FullCowlEffect extends EffectGlyph {
        public FullCowlEffect() {
            super("full_cowl", 14);
            element(Element.ARCANE);
            tier(3);
            minCircle(3);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public int hintDuration() { return 600; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le) || !LhUtil.once(ctx, "cowl:" + le.getId())) return;
            // v1.1: o degrau seguro sobe com a evolução própria do Full Cowl (Shoot Style, Corpo Forjado, Full Bullet)
            int step = Mth.clamp(FullCowl.safeStepFor(ctx.caster() != null ? ctx.caster() : le) + Math.max(0, stats.amplify), 0,
                    FullCowl.LADDER.length - 1);
            float p = FullCowl.LADDER[step];
            int ticks = Mth.clamp(stats.duration(600), 200, 2400);
            FullCowl.activate(le, p, ticks);
            ServerLevel l = ctx.level();
            Vec3 c = LhUtil.center(le);
            ElementFx.shockwave(l, le.position().add(0, 0.1, 0), GREEN, 2.5F + 3F * p, 12, null);
            l.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 20 + Math.round(30 * p), 0.4, 0.8, 0.4, 0.3);
            LhUtil.sound(l, c, SoundEvents.TRIDENT_THUNDER.value(), 0.4F + 0.4F * p, 1.9F);
            LhUtil.sound(l, c, SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.6F);
            float safe = FullCowl.safeFor(le);
            boolean over = p > safe + 1.0E-4F;
            LhUtil.actionbar(le, Component.translatable(over ? "message.isekai_mahou.legends_h.cowl_over" : "message.isekai_mahou.legends_h.cowl_on",
                    Math.round(p * 100), Math.round(safe * 100)).withStyle(over ? ChatFormatting.RED : ChatFormatting.GREEN));
            if (p >= 1F) LhUtil.banner(le, stats, "full_cowl_100", GREEN, true);
        }
    }

    // =====================================================================================
    //  One For All (aumento)
    // =====================================================================================
    /**
     * AUMENTO (Arcano, tier 3, C3). Põe uma porcentagem do One For All na magia: 1 cópia = 20%, 2 = 45%, 3 = 100% —
     * poder ×1,3 / ×1,68 / ×2,5. Acima da porcentagem segura do seu Círculo (+10% com Full Cowl), depois da magia o
     * corpo quebra: dano em si mesmo (nunca mata) e braço quebrado. Regras em {@link Augments}.
     */
    public static AugmentGlyph oneForAll() {
        return new AugmentGlyph("one_for_all", 6, s -> s.custom.merge(LhIds.ONE_FOR_ALL, 1, Integer::sum))
                .<AugmentGlyph>element(Element.ARCANE).<AugmentGlyph>tier(3).minCircle(3);
    }

    /** Porcentagem do One For All nos stats deste efeito ou da forma (0 = sem). */
    static float ofa(SpellContext ctx, SpellStats stats) {
        return Augments.ofaPercent(Math.max(stats.custom(LhIds.ONE_FOR_ALL), ctx.formStats().custom(LhIds.ONE_FOR_ALL)));
    }

    // =====================================================================================
    //  Delaware Smash
    // =====================================================================================
    /**
     * EFEITO (Vento, tier 2, C2). Um peteleco que vira pressão de ar: 4 de dano e um EMPURRÃO enorme para longe de
     * você (chefes quase não se mexem). Em você (Si Mesmo): "Air Force" — a bala de ar sai para onde você olha
     * (20 blocos) e acerta os 3 primeiros inimigos no caminho.
     */
    public static class DelawareSmash extends EffectGlyph {
        public static final float DAMAGE = 4F;

        public DelawareSmash() {
            super("delaware_smash", 10);
            element(Element.WIND);
            tier(2);
            minCircle(2);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            ServerLevel l = ctx.level();
            if (LhUtil.self(ctx, target)) {
                if (c == null || !LhUtil.once(ctx, "delaware_self")) return;
                Vec3 from = c.getEyePosition().add(0, -0.2, 0);
                Vec3 dir = c.getLookAngle();
                Vec3 end = LhUtil.rayEnd(l, from, dir, 20, c);
                List<LivingEntity> hit = foesOnLine(ctx, from, end, 0.9, 3);
                for (int i = 1; i <= 5; i++) {
                    Vec3 at = from.add(end.subtract(from).scale(i / 5.0));
                    BossKit.schedule(l, i, () -> ElementFx.shockwave(l, at, AIR, 1.2F, 8, dir));
                }
                for (int i = 0; i < hit.size(); i++) {
                    LivingEntity e = hit.get(i);
                    int delay = 1 + (int) (e.distanceTo(c) / 4);
                    BossKit.schedule(l, delay, () -> smash(ctx, stats, e, dir));
                }
                l.sendParticles(ParticleTypes.CLOUD, from.x + dir.x, from.y + dir.y, from.z + dir.z, 6, 0.1, 0.1, 0.1, 0.05);
                LhUtil.sound(l, from, SoundEvents.WIND_CHARGE_BURST.value(), 1.2F, 0.8F);
                LhUtil.banner(c, stats, "delaware_air_force", GREEN, false);
                return;
            }
            if (!(target instanceof LivingEntity le)) return;
            Vec3 dir = LhUtil.center(le).subtract(c != null ? c.position().add(0, 1, 0) : LhUtil.center(le).subtract(ctx.look()));
            smash(ctx, stats, le, dir.lengthSqr() < 1.0E-4 ? ctx.look() : dir.normalize());
            if (c != null && LhUtil.once(ctx, "delaware_banner")) LhUtil.banner(c, stats, "delaware_smash", GREEN, false);
        }

        private static void smash(SpellContext ctx, SpellStats stats, LivingEntity e, Vec3 dir) {
            if (!e.isAlive()) return;
            ServerLevel l = ctx.level();
            ctx.damage(e, DAMAGE, stats, null, Element.WIND);
            float pow = Math.min(3F, stats.power()) * (1F + ofa(ctx, stats));
            double k = LhUtil.boss(e) ? 0.25 : 1.4 * pow;
            e.push(dir.x * k, 0.25 + 0.08 * k, dir.z * k);
            e.hurtMarked = true;
            Vec3 c = LhUtil.center(e);
            ElementFx.shockwave(l, c, AIR, 1.8F, 10, dir);
            l.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 8, 0.3, 0.3, 0.3, 0.08);
            LhUtil.sound(l, c, SoundEvents.WIND_CHARGE_BURST.value(), 1.0F, 1.2F);
        }
    }

    // =====================================================================================
    //  Detroit Smash
    // =====================================================================================
    /**
     * EFEITO (Vento, tier 4, C5). O soco que faz o vento: 8 de dano no alvo, que é lançado para cima e para longe, e a
     * onda de choque fere os inimigos em volta (raio 3; Expandir +) com metade. Com One For All a 100% vira o
     * "UNITED STATES OF SMASH": raio dobrado, dano ×1,5 e o chão treme. Em você (Si Mesmo): o soco explode 3 blocos à
     * sua frente. Não quebra blocos.
     */
    public static class DetroitSmash extends EffectGlyph {
        public static final float DAMAGE = 8F;

        public DetroitSmash() {
            super("detroit_smash", 22);
            element(Element.WIND);
            tier(4);
            minCircle(5);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public float hintRadius() { return 3F; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            boolean uss = ofa(ctx, stats) >= 1F;
            if (LhUtil.self(ctx, target)) {
                if (c == null || !LhUtil.once(ctx, "detroit_self")) return;
                Vec3 at = LhUtil.rayEnd(ctx.level(), c.getEyePosition().add(0, -0.3, 0), c.getLookAngle(), 3, c);
                punch(ctx, stats, c, null, at, c.getLookAngle(), uss);
                return;
            }
            if (!(target instanceof LivingEntity le) || !LhUtil.once(ctx, "detroit:" + le.getId())) return;
            Vec3 dir = c != null ? LhUtil.center(le).subtract(c.getEyePosition()) : ctx.look();
            punch(ctx, stats, c, le, LhUtil.center(le), dir.lengthSqr() < 1.0E-4 ? ctx.look() : dir.normalize(), uss);
        }

        private static void punch(SpellContext ctx, SpellStats stats, LivingEntity c, LivingEntity main, Vec3 at, Vec3 dir, boolean uss) {
            ServerLevel l = ctx.level();
            float r = Mth.clamp(stats.radius(3F), 2F, 8F) * (uss ? 2F : 1F);
            float mult = uss ? 1.5F : 1F;
            if (main != null) {
                ctx.damage(main, DAMAGE * mult, stats, null, Element.WIND);
                launch(main, dir, uss ? 1.6 : 1.0);
            }
            for (LivingEntity e : LhUtil.foesAround(ctx, at, r, 16)) {
                if (e == main) continue;
                e.invulnerableTime = 0;
                ctx.damage(e, DAMAGE * 0.5F * mult, stats, null, Element.WIND);
                Vec3 out = e.position().subtract(at);
                launch(e, out.lengthSqr() < 1.0E-4 ? dir : out.normalize(), uss ? 1.0 : 0.6);
            }
            ElementFx.flash(l, at, 0xFFFFFF, uss ? 5F : 2.5F);
            ElementFx.shockwave(l, at, AIR, r, 14, dir);
            ElementFx.shockwave(l, at.add(0, -0.8, 0), GREEN, r * 0.8F, 18, null);
            l.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, uss ? 40 : 16, r * 0.3, 0.4, r * 0.3, 0.12);
            l.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 12, 0.5, 0.5, 0.5, 0.4);
            if (uss) {
                ElementFx.shake(l, at, 40, 1.4F, 16);
                ElementFx.pillar(l, at.add(0, -1, 0), AIR, 1.2F, 14F, 18);
            }
            LhUtil.sound(l, at, SoundEvents.GENERIC_EXPLODE.value(), uss ? 1.6F : 1.0F, uss ? 0.7F : 1.2F);
            LhUtil.sound(l, at, SoundEvents.PLAYER_ATTACK_STRONG, 1.4F, 0.6F);
            if (c != null && LhUtil.once(ctx, "detroit_banner")) {
                LhUtil.banner(c, stats, uss ? "united_states_smash" : "detroit_smash", GREEN, uss);
            }
        }

        private static void launch(LivingEntity e, Vec3 dir, double k) {
            if (LhUtil.boss(e)) k *= 0.15;
            e.push(dir.x * k, 0.45 * k + 0.15, dir.z * k);
            e.hurtMarked = true;
        }
    }

    // =====================================================================================
    //  Blackwhip
    // =====================================================================================
    /**
     * EFEITO (Sombra, tier 2, C2). Tentáculos negros saem do braço: um inimigo atingido é PUXADO até você (chefes só
     * ficam lentos), leva 2 de dano e Lentidão III por 2 s. Num bloco: você se puxa até ele (gancho) e cai devagar por
     * 3 s. Em você (Si Mesmo): o chicote vai para onde você olha — puxa o primeiro inimigo (20 blocos) ou te leva até
     * o bloco (24 blocos).
     */
    public static class Blackwhip extends EffectGlyph {
        public static final float DAMAGE = 2F;

        public Blackwhip() {
            super("blackwhip", 10);
            element(Element.SHADOW);
            tier(2);
            minCircle(2);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public int hintDuration() { return 40; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null) return;
            if (LhUtil.self(ctx, target)) {
                if (!LhUtil.once(ctx, "whip_self")) return;
                LivingEntity foe = LhUtil.firstFoeInSight(ctx, c, 20, 0.96);
                if (foe != null) {
                    pull(ctx, stats, c, foe);
                    return;
                }
                Vec3 eye = c.getEyePosition();
                BlockHitResult r = ctx.level().clip(new ClipContext(eye, eye.add(c.getLookAngle().scale(24)), ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, c));
                if (r.getType() != HitResult.Type.MISS) grapple(ctx, c, r.getLocation());
                return;
            }
            if (target instanceof LivingEntity le) pull(ctx, stats, c, le);
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null || hit.getLocation().distanceToSqr(c.position()) < 9) return;
            grapple(ctx, c, hit.getLocation());
        }

        private static void pull(SpellContext ctx, SpellStats stats, LivingEntity c, LivingEntity e) {
            ServerLevel l = ctx.level();
            tendrils(l, c, LhUtil.center(e));
            ctx.damage(e, DAMAGE, stats, null, Element.SHADOW);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stats.duration(40), 2, false, true), c);
            if (!LhUtil.boss(e)) {
                Vec3 to = c.position().subtract(e.position());
                double d = to.length();
                if (d > 1.5) {
                    Vec3 v = to.normalize().scale(Math.min(1.8, 0.22 * d + 0.3));
                    e.setDeltaMovement(v.x, Math.min(0.9, 0.25 + Math.max(0, to.y) * 0.12), v.z);
                    e.hurtMarked = true;
                }
            }
            LhUtil.sound(l, e.position(), SoundEvents.FISHING_BOBBER_RETRIEVE, 1.2F, 0.6F);
            LhUtil.sound(l, e.position(), SoundEvents.SCULK_VEIN_BREAK, 1.0F, 0.6F);
        }

        private static void grapple(SpellContext ctx, LivingEntity c, Vec3 point) {
            if (!LhUtil.once(ctx, "whip_grapple")) return;
            ServerLevel l = ctx.level();
            tendrils(l, c, point);
            Vec3 to = point.subtract(c.position());
            double d = to.length();
            if (d < 1.5) return;
            Vec3 v = to.normalize().scale(Math.min(2.2, 0.16 * d + 0.6));
            c.setDeltaMovement(v.x, v.y + 0.3, v.z);
            c.hurtMarked = true;
            c.resetFallDistance();
            c.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
            LhUtil.sound(l, c.position(), SoundEvents.FISHING_BOBBER_THROW, 1.2F, 0.5F);
            LhUtil.sound(l, point, SoundEvents.SCULK_VEIN_PLACE, 1.0F, 0.6F);
        }

        /** Dois tentáculos que se torcem do braço até o ponto. */
        private static void tendrils(ServerLevel l, LivingEntity c, Vec3 to) {
            Vec3 from = c.getEyePosition().add(0, -0.45, 0);
            Vec3 d = to.subtract(from);
            Vec3 side = d.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
            int n = Mth.clamp((int) (d.length() * 3), 6, 60);
            for (int k = 0; k < 2; k++) {
                for (int i = 0; i <= n; i++) {
                    double t = i / (double) n;
                    double wave = Math.sin(t * Math.PI * 3 + k * Math.PI) * 0.35 * Math.sin(t * Math.PI);
                    Vec3 p = from.add(d.scale(t)).add(side.scale(wave)).add(0, Math.cos(t * Math.PI * 2 + k) * 0.15, 0);
                    l.sendParticles(LhUtil.dust(i % 4 == 0 ? 0x3C7A4E : WHIP, 1.1F), p.x, p.y, p.z, 1, 0.01, 0.01, 0.01, 0);
                }
            }
        }
    }

    /** Inimigos cuja caixa encosta no segmento {@code from→to}, do mais perto ao mais longe. */
    static List<LivingEntity> foesOnLine(SpellContext ctx, Vec3 from, Vec3 to, double width, int max) {
        AABB box = new AABB(from, to).inflate(width + 1.0);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : ctx.level().getEntitiesOfClass(LivingEntity.class, box, e -> LhUtil.isFoe(ctx, e))) {
            AABB hb = e.getBoundingBox().inflate(width);
            if (hb.contains(from) || hb.clip(from, to).isPresent()) out.add(e);
        }
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(from)));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    private MhaGlyphs() {}
}
