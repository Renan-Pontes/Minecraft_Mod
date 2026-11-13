package com.renanpontes.isekaimahou.feature.legends_h.glyph;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.legends_h.LhIds;
import com.renanpontes.isekaimahou.feature.legends_h.LhUtil;
import com.renanpontes.isekaimahou.feature.legends_h.jojo.Bombs;
import com.renanpontes.isekaimahou.feature.legends_h.jojo.Rewind;
import com.renanpontes.isekaimahou.feature.legends_h.jojo.TimeSkip;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Glifos comuns inspirados em JoJo (homenagens: King Crimson, Epitáfio, Killer Queen, Bites the Dust, Star Platinum).
 * São peças normais do Grimório: combinam com qualquer forma, aumento, maestria e mutação.
 */
public final class JojoGlyphs {
    public static List<Glyph> all() {
        return List.of(new KingCrimson(), epitaph(), new BitesTheDust(), new KillerQueen(), new OraRush());
    }

    // =====================================================================================
    //  King Crimson
    // =====================================================================================
    /**
     * EFEITO (Arcano, tier 4, C5). Em você (Si Mesmo): o tempo é apagado e você anda nele — 2,5 s (Estender +, até 7 s)
     * invisível, sem ser mirado, com tudo atravessando você (e você também não fere ninguém). Ao voltar, o primeiro
     * golpe sai ×1,5 e você vê o Epitáfio por 3 s. Num inimigo: o tempo dele some por 2 s (chefes 1/3, jogadores até
     * 2,5 s) — ele anda no automático, não mira e os ataques dele não acontecem.
     */
    public static class KingCrimson extends EffectGlyph {
        public KingCrimson() {
            super("king_crimson", 30);
            element(Element.ARCANE);
            tier(4);
            minCircle(5);
        }

        @Override
        public int hintDuration() { return 50; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            if (LhUtil.self(ctx, target)) {
                if (!LhUtil.once(ctx, "king_crimson_self")) return;
                TimeSkip.phase(le, Mth.clamp(stats.duration(50), 30, 140));
                LhUtil.banner(le, stats, "king_crimson", TimeSkip.CRIMSON, false);
                return;
            }
            int ticks = Mth.clamp(stats.duration(40), 20, 120);
            if (LhUtil.boss(le)) ticks = Math.max(10, ticks / 3);
            else if (le instanceof Player) ticks = Math.min(ticks, 50);
            TimeSkip.erase(le, ticks);
        }
    }

    // =====================================================================================
    //  Epitáfio (aumento)
    // =====================================================================================
    /**
     * AUMENTO (Arcano, tier 3, C4). Quem a magia ferir fica PREVISTO por 6 s (Estender do efeito +): você vê a linha de
     * para onde ele vai e, se ele vier em você, a linha vermelha do golpe e de que lado vem. O primeiro ataque dele em
     * você é esquivado com um passo para o lado.
     */
    public static AugmentGlyph epitaph() {
        return new AugmentGlyph("epitaph", 6, s -> s.custom.merge(LhIds.EPITAPH, 1, Integer::sum))
                .<AugmentGlyph>element(Element.ARCANE).<AugmentGlyph>tier(3).minCircle(4);
    }

    // =====================================================================================
    //  Bites the Dust
    // =====================================================================================
    /**
     * EFEITO (Arcano, tier 4, C5). Planta uma bomba no TEMPO do alvo: 8 s depois (Estender +, até 30 s), ele volta para
     * onde estava. Você/aliado: recupera a vida perdida nesse meio-tempo (até 40% da máxima × poder; teto 60%), fome
     * e um pouco de mana — e se ia MORRER marcado, o tempo volta na hora (1 vez a cada 2 min). Inimigo: é puxado de
     * volta e fica atordoado 1 s (a vida dele não volta). Ver {@link Rewind}.
     */
    public static class BitesTheDust extends EffectGlyph {
        public BitesTheDust() {
            super("bites_the_dust", 28);
            element(Element.ARCANE);
            tier(4);
            minCircle(5);
        }

        @Override
        public int hintDuration() { return 160; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le) || !LhUtil.once(ctx, "btd:" + le.getId())) return;
            boolean self = LhUtil.self(ctx, target);
            boolean friendly = self || ctx.isAlly(target);
            int ticks = Mth.clamp(stats.duration(160), 60, 600);
            Rewind.mark(le, ctx.caster(), friendly, self, LhUtil.power(ctx, stats), ticks);
            if (ctx.caster() != null && LhUtil.once(ctx, "btd_banner")) {
                LhUtil.banner(ctx.caster(), stats, "bites_the_dust", Rewind.PINK, false);
                LhUtil.actionbar(ctx.caster(), Component.translatable("message.isekai_mahou.legends_h.btd_marked",
                        String.format("%.0f", ticks / 20F)).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
    }

    // =====================================================================================
    //  Killer Queen
    // =====================================================================================
    /**
     * EFEITO (Fogo, tier 3, C3). O que for tocado vira bomba. Criatura: explode em 3 s (Estender +) ou quando você a
     * golpeia de novo — 9 de dano nela e 4,5 em volta (raio 2,5; Expandir +). Bloco: mina de 20 s que explode quando
     * um inimigo chega perto (cratera pequena só onde a destruição é permitida). Em você (Si Mesmo): o bloco para onde
     * você olha (até 6 blocos) vira a mina. Até 3 bombas por vez. Ver {@link Bombs}.
     */
    public static class KillerQueen extends EffectGlyph {
        public KillerQueen() {
            super("killer_queen", 16);
            element(Element.FIRE);
            tier(3);
            minCircle(3);
        }

        @Override
        public boolean wantsBlocks() { return true; }

        @Override
        public float baseDamage() { return Bombs.DAMAGE; }

        @Override
        public float hintRadius() { return 2.5F; }

        @Override
        public int hintDuration() { return 60; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            if (LhUtil.self(ctx, target)) {
                if (!LhUtil.once(ctx, "kq_self")) return;
                Vec3 eye = le.getEyePosition();
                BlockHitResult r = ctx.level().clip(new ClipContext(eye, eye.add(le.getLookAngle().scale(6)), ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, le));
                Vec3 at = r.getType() == HitResult.Type.MISS ? le.position() : r.getLocation();
                Bombs.plantBlock(ctx, stats, at, Mth.clamp(stats.duration(400), 100, 1200));
                return;
            }
            Bombs.plantEntity(ctx, stats, le, Mth.clamp(stats.duration(60), 20, 200));
        }

        @Override
        public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
            if (ctx.caster() != null && hit.getLocation().distanceToSqr(ctx.caster().position()) < 2.25) return;
            Bombs.plantBlock(ctx, stats, hit.getLocation(), Mth.clamp(stats.duration(400), 100, 1200));
        }
    }

    // =====================================================================================
    //  ORA ORA (Star Platinum)
    // =====================================================================================
    /**
     * EFEITO (Arcano, tier 3, C3). Uma rajada de socos de um espírito guardião: 7 socos rápidos (1 de dano cada, em
     * 0,7 s) e o último, mais forte (3) e que arremessa. Em você (Si Mesmo): soca o inimigo mais próximo à frente
     * (até 5 blocos). Para no meio se você se afastar mais de 8 blocos.
     */
    public static class OraRush extends EffectGlyph {
        public static final int COLOR = 0x8A6CFF;
        public static final int HITS = 7;

        public OraRush() {
            super("ora_rush", 14);
            element(Element.ARCANE);
            tier(3);
            minCircle(3);
        }

        @Override
        public float baseDamage() { return HITS + 3F; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            LivingEntity t = target instanceof LivingEntity le ? le : null;
            if (LhUtil.self(ctx, target)) {
                if (c == null || !LhUtil.once(ctx, "ora_self")) return;
                t = LhUtil.firstFoeInSight(ctx, c, 5, 0.5);
                if (t == null) {
                    Vec3 p = c.getEyePosition().add(c.getLookAngle().scale(1.5));
                    ctx.level().sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 20, 0.4, 0.4, 0.4, 0.2);
                    LhUtil.sound(ctx.level(), p, SoundEvents.PLAYER_ATTACK_SWEEP, 1F, 1.4F);
                    return;
                }
            }
            if (t == null || !LhUtil.once(ctx, "ora:" + t.getId())) return;
            rush(ctx, stats, c, t);
        }

        private static void rush(SpellContext ctx, SpellStats stats, LivingEntity c, LivingEntity t) {
            ServerLevel l = ctx.level();
            if (c != null) {
                ElementFx.aura(l, c, Element.ARCANE, COLOR, 1.1F, 3, HITS * 2 + 6);
                LhUtil.banner(c, stats, "ora_rush", COLOR, false);
            }
            for (int i = 0; i <= HITS; i++) {
                final int n = i;
                BossKit.schedule(l, i * 2, () -> {
                    if (!t.isAlive() || t.level() != l) return;
                    if (c != null && (!c.isAlive() || c.distanceToSqr(t) > 64)) return;
                    boolean last = n == HITS;
                    t.invulnerableTime = 0;
                    ctx.damage(t, last ? 3F : 1F, stats, null);
                    Vec3 tc = LhUtil.center(t);
                    Vec3 from = c != null ? c.getEyePosition() : tc.subtract(ctx.look());
                    Vec3 dir = tc.subtract(from);
                    dir = dir.lengthSqr() < 1.0E-4 ? ctx.look() : dir.normalize();
                    Vec3 face = tc.subtract(dir.scale(t.getBbWidth() * 0.6));
                    l.sendParticles(ParticleTypes.CRIT, face.x, face.y, face.z, last ? 14 : 5, 0.3, 0.3, 0.3, 0.3);
                    l.sendParticles(LhUtil.dust(COLOR, 1.2F), face.x, face.y, face.z, last ? 10 : 3, 0.35, 0.3, 0.35, 0);
                    if (last) {
                        if (!LhUtil.boss(t)) {
                            t.knockback(1.6F * Math.min(2F, stats.power()), -dir.x, -dir.z);
                            t.hurtMarked = true;
                        }
                        ElementFx.shockwave(l, face, COLOR, 2.2F, 8, dir);
                        LhUtil.sound(l, tc, SoundEvents.PLAYER_ATTACK_STRONG, 1.3F, 0.7F);
                        LhUtil.sound(l, tc, SoundEvents.ANVIL_LAND, 0.5F, 1.6F);
                    } else {
                        LhUtil.sound(l, tc, n % 2 == 0 ? SoundEvents.PLAYER_ATTACK_WEAK : SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.8F,
                                1.1F + 0.05F * n);
                    }
                    if (c != null && n % 2 == 1) {
                        LhUtil.actionbar(c, Component.literal("ORA ".repeat(Math.min(6, 1 + n / 2)).trim() + (last ? "!" : "...")).withStyle(
                                ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
                    }
                });
            }
        }
    }

    private JojoGlyphs() {}
}
