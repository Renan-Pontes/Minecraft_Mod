package com.renanpontes.isekaimahou.feature.legends_f.glyph;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.legends_f.LfNet;
import com.renanpontes.isekaimahou.feature.legends_f.LfUtil;
import com.renanpontes.isekaimahou.feature.legends_f.Transformations;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Técnicas de origem do BRUXO (pactos com demônios): Pacto da Motosserra (homenagem a Chainsaw Man), Contrato
 * Faustiano e "Bang!" (o contrato do demônio do controle, homenagem à cena do dedo-arma).
 */
public final class WarlockTechniques {
    public static List<EffectGlyph> all() {
        return List.of(new Chainsaw(), new Faust(), new Bang());
    }

    // =====================================================================================
    //  Pacto da Motosserra
    // =====================================================================================
    /**
     * Puxa a cordinha do coração: motosserras saem da cabeça e dos braços por 30 s. Golpes corpo a corpo +7 de dano,
     * fazem Sangrar e devolvem 25% como vida (sangue cura o demônio); correndo, rasga quem está na frente. Uma vez por
     * transformação, um golpe fatal deixa você de pé com 30% da vida (o motor do coração liga de novo).
     */
    public static class Chainsaw extends EffectGlyph {
        public Chainsaw() {
            super("warlock_chainsaw", 30);
            element(Element.FIRE);
            origin(MagicPath.WARLOCK);
            tier(4);
            minCircle(5);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public int hintDuration() { return 600; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof ServerPlayer sp) || target != ctx.caster() || LfUtil.isFake(sp)) return;
            if (!LfUtil.oncePerTick(ctx, "chainsaw")) return;
            Transformations.startChainsaw(sp, Mth.clamp(stats.duration(600), 200, 1800), Math.max(0, stats.amplify));
        }
    }

    // =====================================================================================
    //  Contrato Faustiano
    // =====================================================================================
    /**
     * Assina um contrato com o demônio (60 s): +0,4 de Poder Mágico e regeneração de mana ×2, mas −30% de vida máxima.
     * Cláusula: derrotar 3 inimigos (+1 por Amplificar) antes do prazo. Cumprido: a vida volta cheia, mana e XP de
     * recompensa. Descumprido: o demônio cobra — 25% da vida, Fraqueza e Lentidão.
     */
    public static class Faust extends EffectGlyph {
        public Faust() {
            super("warlock_faust", 16);
            element(Element.FIRE);
            origin(MagicPath.WARLOCK);
            tier(3);
            minCircle(4);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public int hintDuration() { return 1200; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof ServerPlayer sp) || target != ctx.caster() || LfUtil.isFake(sp)) return;
            if (!LfUtil.oncePerTick(ctx, "faust")) return;
            Transformations.startFaust(sp, Mth.clamp(stats.duration(1200), 400, 3600), 3 + Math.max(0, stats.amplify));
        }
    }

    // =====================================================================================
    //  Bang!
    // =====================================================================================
    /**
     * Você aponta o dedo como uma arma e diz "Bang.": meio segundo depois o alvo explode por dentro — 24 de dano que
     * fura metade da armadura e joga longe. Sem projétil visível, sem como desviar depois de marcado.
     */
    public static class Bang extends EffectGlyph {
        public static final float DAMAGE = 24F;

        public Bang() {
            super("warlock_bang", 34);
            element(Element.SHADOW);
            origin(MagicPath.WARLOCK);
            tier(4);
            minCircle(6);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le) || !le.isAlive() || target == ctx.caster()) return;
            ServerLevel level = ctx.level();
            if (ctx.caster() != null && LfUtil.oncePerTick(ctx, "bang_hand")) {
                var c = ctx.caster();
                Vec3 hand = c.getEyePosition().add(c.getLookAngle().scale(0.8)).add(0, -0.35, 0);
                LfUtil.fx(level, LfNet.MUZZLE, hand, c.getLookAngle(), 0xFF2A2A, 6, 0.5F);
                LfUtil.sound(level, c.position(), SoundEvents.FIREWORK_ROCKET_BLAST, 1.0F, 0.6F);
                LfUtil.banner(c, "announce.isekai_mahou.legends_f.bang", 0xD01818, false);
            }
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, le.getX(), le.getY() + le.getBbHeight(), le.getZ(), 3, 0.2, 0.1, 0.2, 0);
            SpellScheduler.schedule(10, () -> boom(ctx, le, stats));
        }

        private static void boom(SpellContext ctx, LivingEntity le, SpellStats stats) {
            if (!le.isAlive() || le.level() != ctx.level()) return;
            ServerLevel level = ctx.level();
            Vec3 c = le.position().add(0, le.getBbHeight() * 0.6, 0);
            float armor = le.getArmorValue();
            float raw = DAMAGE * (1F + armor / 40F);
            ctx.damage(le, raw, stats, null);
            Vec3 from = ctx.caster() != null ? ctx.caster().position() : c;
            Vec3 push = c.subtract(from).multiply(1, 0, 1);
            if (push.lengthSqr() > 1.0E-4) {
                push = push.normalize();
                le.knockback(1.2, -push.x, -push.z);
            }
            LfUtil.fx(level, LfNet.BANG, c, new Vec3(0, 1, 0), 0xE01818, 14, 1.4F + le.getBbWidth());
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.2);
            level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            LfUtil.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 1.6F);
            LfUtil.sound(level, c, SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 1.0F, 0.5F);
        }
    }

    private WarlockTechniques() {}
}
