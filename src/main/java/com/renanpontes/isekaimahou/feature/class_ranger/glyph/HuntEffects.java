package com.renanpontes.isekaimahou.feature.class_ranger.glyph;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerCompanions;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerFx;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerGlyphIds;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerMarks;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.RangerSteedEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;
import com.renanpontes.isekaimahou.glyph.weapon.WeaponFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Efeitos de CAÇADA do Ranger (glifos de origem): Marca do Caçador, Chamado da Matilha e Chamado do Corcel. Os dois
 * chamados funcionam em si mesmo (forma Si Mesmo: chama/cura/monta) ou num inimigo (forma Flecha/Projétil: manda os
 * companheiros atacarem / o Corcel investe).
 */
public final class HuntEffects {
    public static final int MARK_RGB = 0xE04A3B;

    private HuntEffects() {}

    // =====================================================================================
    //  Marca do Caçador
    // =====================================================================================

    /**
     * Marca do Caçador: fere um pouco e MARCA o alvo por 12 s — brilha através das paredes, a mira gira sobre ele e ele
     * leva +25% de dano seu e dos seus companheiros (+10% por Amplificar). Os companheiros caçam os marcados primeiro.
     */
    public static class HuntersMark extends EffectGlyph {
        public static final int DURATION = 240;

        public HuntersMark() {
            super(RangerGlyphIds.MARK, 8);
            origin(MagicPath.RANGER);
            tier(1);
        }

        @Override
        public float baseDamage() { return 2.0F; }

        @Override
        public int hintDuration() { return DURATION; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity caster = ctx.caster();
            if (!(target instanceof LivingEntity le) || caster == null || le == caster) return;
            ctx.damage(le, 2.0F, stats, null);
            if (!le.isAlive()) return;
            float bonus = 0.25F + 0.10F * Math.max(0, stats.amplify);
            RangerMarks.mark(le, caster, stats.duration(DURATION), MARK_RGB, bonus);
            ServerLevel level = ctx.level();
            RangerFx.crosshair(level, le, MARK_RGB, level.getGameTime());
            RangerFx.ring(level, le.position().add(0, 0.1, 0), le.getBbWidth() + 0.5, 16, WeaponFx.dust(MARK_RGB, 1.1F), 0);
            RangerFx.sound(level, le.position(), SoundEvents.BELL_BLOCK, 0.5F, 1.8F);
        }
    }

    // =====================================================================================
    //  Chamado da Matilha
    // =====================================================================================

    /**
     * Chamado da Matilha: em si mesmo, chama o Lobo (e a Águia, a partir do rank I da Trilha) para o seu lado, cura 30%
     * e dá Regeneração; num inimigo, os companheiros aparecem (se preciso) e atacam o alvo com Força e Velocidade.
     * Amplificar deixa a cura e a Força mais fortes.
     */
    public static class PackCall extends EffectGlyph {
        public PackCall() {
            super(RangerGlyphIds.PACK, 10);
            origin(MagicPath.RANGER);
            tier(1);
        }

        @Override
        public int hintDuration() { return 100; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(ctx.caster() instanceof ServerPlayer sp)) return;
            boolean self = target == sp;
            if (!self && !(target instanceof LivingEntity)) return;
            boolean ok = RangerCompanions.call(sp, self ? null : (LivingEntity) target, stats.power());
            if (!ok) return;
            ServerLevel level = ctx.level();
            Vec3 at = self ? sp.position() : target.position();
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 1.0, at.z, 8, 0.5, 0.5, 0.5, 0);
            if (!self && target instanceof LivingEntity le) {
                // o lobo fareja: a presa fica marcada por pouco tempo
                RangerMarks.mark(le, sp, 80, MARK_RGB, 0.10F);
            }
        }
    }

    // =====================================================================================
    //  Chamado do Corcel
    // =====================================================================================

    /**
     * Chamado do Corcel: em si mesmo, o Corcel do Ranger aparece selado e você já monta (montado: galope — Velocidade e
     * Salto por 8 s). Num inimigo, o Corcel surge e INVESTE: empurra e fere tudo no caminho até o alvo.
     */
    public static class SteedCall extends EffectGlyph {
        public static final float CHARGE_DAMAGE = 4.5F;

        public SteedCall() {
            super(RangerGlyphIds.STEED, 12);
            origin(MagicPath.RANGER);
            tier(2);
        }

        @Override
        public float baseDamage() { return CHARGE_DAMAGE; }

        @Override
        public int hintDuration() { return 160; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(ctx.caster() instanceof ServerPlayer sp)) return;
            ServerLevel level = ctx.level();
            if (target == sp) {
                if (sp.getVehicle() instanceof RangerSteedEntity steed) {
                    gallop(level, steed, stats);
                    return;
                }
                RangerCompanions.callSteed(sp);
                return;
            }
            if (!(target instanceof LivingEntity le)) return;
            RangerSteedEntity steed = sp.getVehicle() instanceof RangerSteedEntity s ? s : RangerCompanions.callSteed(sp);
            if (steed == null) return;
            charge(ctx, level, steed, le, stats);
        }

        private static void gallop(ServerLevel level, RangerSteedEntity steed, SpellStats stats) {
            int ticks = stats.duration(160);
            steed.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 1 + Math.max(0, stats.amplify)));
            steed.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks, 1));
            steed.neigh();
            level.sendParticles(ParticleTypes.CLOUD, steed.getX(), steed.getY() + 0.4, steed.getZ(), 14, 0.6, 0.2, 0.6, 0.06);
            RangerFx.sound(level, steed.position(), SoundEvents.HORSE_GALLOP, 1.2F, 0.9F);
        }

        /** Investida: o corcel avança até o alvo; quem estiver no caminho (2 blocos) é empurrado e ferido. */
        private static void charge(SpellContext ctx, ServerLevel level, RangerSteedEntity steed, LivingEntity target, SpellStats stats) {
            Vec3 from = steed.position();
            Vec3 to = target.position();
            Vec3 dir = to.subtract(from).multiply(1, 0, 1);
            double len = Math.min(14, dir.length());
            if (len < 0.5) return;
            dir = dir.normalize();
            steed.setDeltaMovement(dir.x * 1.6, 0.25, dir.z * 1.6);
            steed.hasImpulse = true;
            steed.hurtMarked = true;
            steed.neigh();
            Vec3 mid = from.add(dir.scale(len / 2));
            for (LivingEntity e : ctx.enemiesAround(mid, len / 2 + 2)) {
                Vec3 rel = e.position().subtract(from);
                double along = rel.dot(dir);
                if (along < -1 || along > len + 1.5 || rel.subtract(dir.scale(along)).length() > 2.0 + e.getBbWidth() * 0.5) continue;
                e.invulnerableTime = 0;
                ctx.damage(e, CHARGE_DAMAGE, stats, steed);
                double kb = (1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)) * (0.9 + 0.2 * stats.power());
                e.push(dir.x * kb, 0.45, dir.z * kb);
                e.hurtMarked = true;
            }
            RangerFx.line(level, from.add(0, 0.3, 0), from.add(dir.scale(len)).add(0, 0.3, 0), 0.8, ParticleTypes.CLOUD);
            RangerFx.sound(level, from, SoundEvents.HORSE_ANGRY, 1.2F, 0.9F);
            RangerFx.sound(level, to, SoundEvents.HORSE_LAND, 1.2F, 0.8F);
        }
    }
}
