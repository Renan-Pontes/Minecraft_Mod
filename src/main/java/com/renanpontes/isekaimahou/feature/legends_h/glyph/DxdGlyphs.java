package com.renanpontes.isekaimahou.feature.legends_h.glyph;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.legends_h.Augments;
import com.renanpontes.isekaimahou.feature.legends_h.LhIds;
import com.renanpontes.isekaimahou.feature.legends_h.LhRegistry;
import com.renanpontes.isekaimahou.feature.legends_h.LhUtil;
import com.renanpontes.isekaimahou.glyph.AugmentGlyph;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Glifos comuns inspirados em High School DxD (homenagens à Boost Gear do Dragão Vermelho): o aumento Boost, o efeito
 * Transfer! e o efeito Balance Breaker (Scale Mail).
 */
public final class DxdGlyphs {
    public static final int RED = 0xD0102A;
    public static final int GEM = 0x3CFF6A;

    public static List<Glyph> all() {
        return List.of(boost(), new Transfer(), new BalanceBreaker());
    }

    // =====================================================================================
    //  Boost (aumento)
    // =====================================================================================
    /**
     * AUMENTO (Arcano, tier 3, C3, +0,25 s de conjuração). "Boost!": conjurar a magia de novo em até 2,5 s (ou
     * mantê-la canalizada) dobra o bônus a cada passo — ×1 → ×1,25 → ×1,5 → ×2 → ×3 (teto pelo Círculo). Parar zera.
     * Regras em {@link Augments}.
     */
    public static AugmentGlyph boost() {
        return new AugmentGlyph("boost_gear", 8, s -> s.custom.merge(LhIds.BOOST_GEAR, 1, Integer::sum))
                .castTicks(5).<AugmentGlyph>element(Element.ARCANE).<AugmentGlyph>tier(3).minCircle(3);
    }

    // =====================================================================================
    //  Transfer!
    // =====================================================================================
    /**
     * EFEITO (Arcano, tier 3, C3, benéfico). Entrega o poder acumulado: o alvo (aliado ou você) ganha "Transfer!" por
     * 15 s (Estender +) — dano +15% por nível, corpo a corpo e magia. Nível = 1 + o passo de Boost em que você está
     * + Amplificar (máx. 5). Quem transfere volta ao passo zero.
     */
    public static class Transfer extends EffectGlyph {
        public Transfer() {
            super("boost_transfer", 10);
            element(Element.ARCANE);
            tier(3);
            minCircle(3);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public int hintDuration() { return 300; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            LivingEntity c = ctx.caster();
            int step = c != null ? Augments.boostStep(c) : 0;
            int level = Mth.clamp(1 + step + Math.max(0, stats.amplify), 1, 5);
            int ticks = Mth.clamp(stats.duration(300), 100, 1200);
            le.addEffect(new MobEffectInstance(LhRegistry.BOOSTED, ticks, level - 1, false, true, true), c);
            ServerLevel l = ctx.level();
            Vec3 p = LhUtil.center(le);
            if (c != null && c != le) LhUtil.line(l, c.getEyePosition().add(0, -0.4, 0), p, GEM, 1.0F, 24);
            ElementFx.aura(l, le, Element.FIRE, GEM, 0.9F, 2, 30);
            l.sendParticles(LhUtil.dust(GEM, 1.4F), p.x, p.y, p.z, 12, 0.3, 0.5, 0.3, 0);
            LhUtil.sound(l, p, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0F, 1.2F);
            LhUtil.sound(l, p, SoundEvents.BEACON_POWER_SELECT, 0.7F, 1.6F);
            LhUtil.actionbar(le, Component.translatable("message.isekai_mahou.legends_h.transfer", level).withStyle(ChatFormatting.GREEN));
            if (c != null && LhUtil.once(ctx, "transfer_reset")) Augments.resetBoost(c);
        }
    }

    // =====================================================================================
    //  Balance Breaker
    // =====================================================================================
    /**
     * EFEITO (Fogo, tier 4, C5, benéfico). Veste a Scale Mail do Dragão Vermelho por 20 s (Estender +; ×1,5 se você
     * estiver no passo 2+ do Boost): armadura +8, resistência +4, ataque +3, velocidade +10%, meio imune a empurrão.
     * Ao acabar, o corpo cansa: Fraqueza por 5 s.
     */
    public static class BalanceBreaker extends EffectGlyph {
        public BalanceBreaker() {
            super("balance_breaker", 26);
            element(Element.FIRE);
            tier(4);
            minCircle(5);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public int hintDuration() { return 400; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le) || !LhUtil.once(ctx, "bb:" + le.getId())) return;
            LivingEntity c = ctx.caster();
            int ticks = Mth.clamp(stats.duration(400), 100, 1200);
            if (c != null && Augments.boostStep(c) >= 2) ticks = Math.round(ticks * 1.5F);
            le.addEffect(new MobEffectInstance(LhRegistry.SCALE_MAIL, ticks, 0, false, true, true), c);
            ServerLevel l = ctx.level();
            Vec3 p = LhUtil.center(le);
            ElementFx.flash(l, p, 0xFF4050, 3F);
            ElementFx.shockwave(l, le.position().add(0, 0.2, 0), RED, 5F, 14, null);
            ElementFx.aura(l, le, Element.FIRE, RED, 1.2F, 4, ticks);
            l.sendParticles(LhUtil.dust(GEM, 1.6F), p.x, p.y, p.z, 10, 0.4, 0.6, 0.4, 0);
            l.sendParticles(LhUtil.dust(RED, 2.0F), p.x, p.y, p.z, 30, 0.5, 0.8, 0.5, 0);
            LhUtil.sound(l, p, SoundEvents.ENDER_DRAGON_GROWL, 0.5F, 1.4F);
            LhUtil.sound(l, p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.2F, 0.8F);
            LhUtil.banner(le, stats, "balance_breaker", RED, true);
            final int total = ticks;
            BossKit.schedule(l, total + 2, () -> {
                if (le.isAlive() && !le.hasEffect(LhRegistry.SCALE_MAIL)) {
                    le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0, false, true));
                    LhUtil.actionbar(le, Component.translatable("message.isekai_mahou.legends_h.scale_mail_off").withStyle(ChatFormatting.GRAY));
                }
            });
        }
    }

    private DxdGlyphs() {}
}
