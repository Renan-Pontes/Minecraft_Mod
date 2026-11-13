package com.renanpontes.isekaimahou.feature.legends_g.glyph;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.api.Domains;
import com.renanpontes.isekaimahou.core.api.SpellStack;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.arcane.ArcaneUtil;
import com.renanpontes.isekaimahou.feature.domain.entity.DomainDomeEntity;
import com.renanpontes.isekaimahou.feature.legends_g.LgEvents;
import com.renanpontes.isekaimahou.feature.legends_g.LgRegistry;
import com.renanpontes.isekaimahou.feature.legends_g.LgUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Glifos de Asta (homenagem a "Black Clover" — o garoto sem mana das espadas negras). COMUNS, do caminho da Sombra:
 * <ul>
 *   <li>{@link DemonSlayerSword} Espada Matadora de Demônios (EFEITO) — corte de ANTI-MAGIA: fere, arranca bênçãos,
 *       interrompe, contraria a conjuração do alvo, come magias soltas e parte o domínio em que o alvo está.</li>
 *   <li>{@link BlackForm} Forma Negra (EFEITO, benéfico) — o "Asta Negro": força, velocidade e pele que bebe magia.</li>
 * </ul>
 * (O Cavaleiro tem a SUA Lâmina Anti-Magia de classe; esta é a versão de glifo comum, para qualquer magia.)
 */
public final class AstaTechniques {
    public static final int COLOR = 0x2A2A2A;
    public static final int EDGE = 0x8FA08A;

    /** Glifos COMUNS: o elemento + tier os colocam no caminho da Sombra e nos sorteios de pergaminhos. */
    public static List<EffectGlyph> all() {
        return List.of(new DemonSlayerSword(), new BlackForm());
    }

    // =====================================================================================
    //  Espada Matadora de Demônios
    // =====================================================================================
    /**
     * EFEITO (Sombra, tier 3). No alvo: corte negro de dano 10 (×1,5 em criaturas MÁGICAS — chefes, monstros do mod,
     * jogadores, quem está com bênçãos), arranca até 2 efeitos bons, interrompe a conjuração de jogadores, Silencia 2 s,
     * CONTRARIA as conjurações pendentes do próprio alvo (poder ×2), devora magias inimigas soltas a até 2,5 blocos e
     * PARTE o domínio inimigo em que o alvo está (de chefe: −20% da barreira). Com Si Mesmo: o corte voa em linha
     * (10 blocos, Expandir +) e faz tudo isso em quem cruzar. Na Forma Negra: ×1,3.
     */
    public static class DemonSlayerSword extends EffectGlyph {
        public static final float DAMAGE = 10F;

        public DemonSlayerSword() {
            super("lg_demon_slayer_sword", 22);
            element(Element.SHADOW);
            tier(3);
            minCircle(3);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null) return;
            if (LgUtil.self(ctx, target)) {
                if (LgUtil.once(ctx, "demon_slayer_line")) slash(ctx, stats, c, c.getLookAngle());
                return;
            }
            if (!(target instanceof LivingEntity le) || !LgUtil.once(ctx, "demon_slayer:" + le.getId())) return;
            Vec3 dir = LgUtil.center(le).subtract(c.getEyePosition());
            dir = dir.lengthSqr() < 1.0E-4 ? c.getLookAngle() : dir.normalize();
            int[] tally = new int[3];
            cut(ctx, stats, c, le, dir, tally);
            ElementFx.slash(ctx.level(), LgUtil.center(le), dir, Element.SHADOW, boost(c) > 1F ? 0x050505 : COLOR, 2.6F, 3);
            LgUtil.sound(ctx.level(), le.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.5F);
            report(ctx, c, tally);
        }
    }

    static float boost(LivingEntity c) {
        return c.hasEffect(LgRegistry.BLACK_FORM) ? 1.3F : 1F;
    }

    /** Um corte anti-magia num alvo. {@code tally} = [magias cortadas, bênçãos arrancadas, domínios partidos]. */
    static void cut(SpellContext ctx, SpellStats stats, LivingEntity c, LivingEntity e, Vec3 dir, int[] tally) {
        ServerLevel l = ctx.level();
        float power = Math.max(0.5F, stats.power() * ctx.powerMult());
        float mult = LgUtil.magical(e) ? 1.5F : 1F;
        ctx.damage(e, DemonSlayerSword.DAMAGE * mult * boost(c), stats, c, Element.ARCANE);
        tally[1] += LgUtil.stripBuffs(e, 2);
        if (e instanceof ServerPlayer sp) SpellCaster.interrupt(sp);
        Statuses.apply(e, Statuses.Kind.SILENCED, 1, 40, c, power);
        // Conjurações pendentes do próprio alvo
        for (SpellStack.Pending p : SpellStack.near(l, e.position(), 4.0)) {
            if (p.active() && p.counterable() && p.caster() == e && SpellStack.isHostile(p, c)
                    && SpellStack.counter(l, p, c, 2F * power) == SpellStack.CounterResult.COUNTERED) tally[0]++;
        }
        tally[0] += ArcaneUtil.destroyHostileMagic(ctx, LgUtil.center(e), 2.5);
        Domains.Info d = Domains.at(l, e.position());
        if (d != null) tally[2] += breakDomain(ctx, c, d);
        e.knockback(0.4F, -dir.x, -dir.z);
        l.sendParticles(ParticleTypes.SQUID_INK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 6, 0.3, 0.3, 0.3, 0.03);
    }

    /** Parte (ou racha, se for de chefe) um domínio inimigo — uma vez por domínio por conjuração. */
    static int breakDomain(SpellContext ctx, LivingEntity c, Domains.Info d) {
        LivingEntity owner = d.owner();
        if (owner == null || owner == c || ctx.isAlly(owner) || !LgUtil.once(ctx, "domain:" + d.id())) return 0;
        if (Statuses.isBossLike(owner)) {
            Domains.BarrierHandler h = Domains.barrierHandler();
            if (h == null || !d.closed()) return 0;
            h.damage(d.id(), h.maxIntegrity(d.id()) * 0.2F, d.contains(c), c);
            return 1;
        }
        Entity dome = ctx.level().getEntity(d.id());
        if (!(dome instanceof DomainDomeEntity dd)) return 0;
        dd.shatter(c);
        return 1;
    }

    /** Si Mesmo: o corte anti-magia voa em linha reta (para em parede). */
    static void slash(SpellContext ctx, SpellStats stats, LivingEntity c, Vec3 dir) {
        ServerLevel l = ctx.level();
        float len = Mth.clamp(stats.radius(10F), 5F, 24F) * boost(c);
        Vec3 from = c.getEyePosition().add(0, -0.35, 0).add(dir.scale(0.6));
        Vec3 end = LgUtil.rayEnd(l, from, dir, len, c);
        float power = Math.max(0.5F, stats.power() * ctx.powerMult());
        int[] tally = new int[3];
        for (LivingEntity e : LgUtil.foesOnLine(ctx, from, end, 1.0, 10)) {
            if (LgUtil.once(ctx, "demon_slayer:" + e.getId())) cut(ctx, stats, c, e, dir, tally);
        }
        // Conjurações inimigas pendentes perto da linha
        Vec3 mid = from.add(end).scale(0.5);
        for (SpellStack.Pending p : SpellStack.near(l, mid, from.distanceTo(end) * 0.5 + 4)) {
            if (!p.active() || !p.counterable() || !SpellStack.isHostile(p, c)) continue;
            if (distToSegment(p.pos(), from, end) <= 3.0
                    && SpellStack.counter(l, p, c, 2F * power) == SpellStack.CounterResult.COUNTERED) tally[0]++;
        }
        double total = from.distanceTo(end);
        for (double t = 0; t <= total; t += 3.0) tally[0] += ArcaneUtil.destroyHostileMagic(ctx, from.add(dir.scale(t)), 2.0);
        for (Domains.Info d : Domains.active(l)) {
            if (distToSegment(d.center(), from, end) <= d.radius() + 1.0) tally[2] += breakDomain(ctx, c, d);
        }
        int edge = boost(c) > 1F ? 0x050505 : COLOR;
        for (int k = 0; k < 3; k++) {
            ElementFx.slash(l, from.add(dir.scale(Math.min(total, 1.2 + total * k / 3.0))), dir, Element.SHADOW, edge, 3F + k * 0.6F, 3);
        }
        int points = Math.min(16, (int) (total / 0.8));
        for (int i = 0; i <= points; i++) {
            Vec3 p = from.add(dir.scale(total * i / Math.max(1, points)));
            l.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y, p.z, 2, 0.2, 0.2, 0.2, 0.01);
        }
        ElementFx.shockwave(l, from, EDGE, 1.4F, 8, dir);
        LgUtil.sound(l, from, SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F, 0.5F);
        LgUtil.sound(l, from, SoundEvents.WITHER_SHOOT, 0.5F, 0.7F);
        report(ctx, c, tally);
    }

    private static void report(SpellContext ctx, LivingEntity c, int[] tally) {
        if (tally[0] + tally[1] + tally[2] <= 0) return;
        LgUtil.sound(ctx.level(), c.position(), SoundEvents.GLASS_BREAK, 0.9F, 0.6F);
        LgUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_g.anti_magic", tally[0], tally[1], tally[2])
                .withStyle(ChatFormatting.DARK_GREEN));
    }

    /** Distância de um ponto até o segmento a→b. */
    static double distToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        if (len2 < 1.0E-6) return p.distanceTo(a);
        double t = Mth.clamp(p.subtract(a).dot(ab) / len2, 0.0, 1.0);
        return p.distanceTo(a.add(ab.scale(t)));
    }

    // =====================================================================================
    //  Forma Negra
    // =====================================================================================
    /**
     * EFEITO (Sombra, tier 4, benéfico): 10 s (Estender +) de Asta Negro no alvo (você com Si Mesmo, ou um aliado):
     * +4 de ataque, +15% de velocidade de ataque, +20% de movimento, +3 de armadura, 30% de resistência a empurrão; dano
     * de MAGIA recebido −40% (de chefes −25%); Silêncio e Corrupção saem; golpes corpo a corpo arrancam 1 efeito bom e
     * interrompem conjurações de jogadores. Ao acabar, o corpo pesa (Fraqueza 6 s). Regras em {@code LgEvents}.
     */
    public static class BlackForm extends EffectGlyph {
        public BlackForm() {
            super("lg_black_form", 28);
            element(Element.SHADOW);
            tier(4);
            minCircle(4);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public int hintDuration() { return 200; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le) || !LgUtil.once(ctx, "black_form:" + le.getId())) return;
            int ticks = Mth.clamp(stats.duration(200), 80, 600);
            le.addEffect(new MobEffectInstance(LgRegistry.BLACK_FORM, ticks, 0, false, false, true), ctx.caster());
            LgEvents.blackFormStarted(le);
            ServerLevel l = ctx.level();
            Vec3 p = le.position();
            ElementFx.aura(l, le, Element.SHADOW, 0x1A1A1A, 1.2F, 3, Math.min(ticks, 200));
            ElementFx.shockwave(l, p.add(0, 0.1, 0), EDGE, 3.5F, 14, null);
            l.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y + 1, p.z, 24, 0.5, 0.8, 0.5, 0.05);
            LgUtil.sound(l, p, SoundEvents.WITHER_AMBIENT, 0.8F, 0.5F);
            LgUtil.sound(l, p, SoundEvents.BEACON_ACTIVATE, 1.0F, 0.5F);
            LgUtil.actionbar(le, Component.translatable("message.isekai_mahou.legends_g.black_form_on", ticks / 20)
                    .withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    private AstaTechniques() {}
}
