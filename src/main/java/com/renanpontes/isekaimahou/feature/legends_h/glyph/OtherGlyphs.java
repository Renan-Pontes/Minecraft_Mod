package com.renanpontes.isekaimahou.feature.legends_h.glyph;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.legends_h.LhIds;
import com.renanpontes.isekaimahou.feature.legends_h.LhUtil;
import com.renanpontes.isekaimahou.glyph.AugmentGlyph;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Outros glifos comuns de anime: Explosão Psíquica 100% (homenagem a Mob Psycho 100), o aumento Genki Dama (homenagem
 * a Dragon Ball) e a Fala Amaldiçoada (homenagem a Jujutsu Kaisen).
 */
public final class OtherGlyphs {
    public static final int PSY = 0xC8F0FF;
    public static final int PSY_MAX = 0xFF60C0;
    public static final int CURSE = 0x7A3AB0;

    public static List<Glyph> all() {
        return List.of(new PsychoHundred(), genkiDama(), new CursedSpeech());
    }

    // =====================================================================================
    //  Explosão Psíquica 100%
    // =====================================================================================
    /**
     * EFEITO (Arcano, tier 4, C5). A emoção vira poder psíquico: o alvo é ERGUIDO no ar e 0,7 s depois ESMAGADO no chão
     * (dano 4 + 0,05 × emoção). A emoção é a vida que VOCÊ já perdeu (10% + 90% × vida perdida); com 25% de vida ou
     * menos ela estoura — "???%": dano ×1,4, área maior e você desmaia um pouco depois (lento e fraco por 6 s).
     * Chefes não sobem (levam 70%). Em você (Si Mesmo): explode em volta (raio 4 + 0,04 × emoção; até 10 inimigos).
     */
    public static class PsychoHundred extends EffectGlyph {
        public PsychoHundred() {
            super("psycho_hundred", 24);
            element(Element.ARCANE);
            tier(4);
            minCircle(5);
        }

        @Override
        public float baseDamage() { return 6F; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            boolean max = c instanceof Player && c.getHealth() <= c.getMaxHealth() * 0.25F;
            int pct = max ? 100 : emotion(c);
            if (c != null && LhUtil.once(ctx, "psycho_banner")) announce(ctx, stats, c, pct, max);
            if (LhUtil.self(ctx, target)) {
                if (c == null || !LhUtil.once(ctx, "psycho_self")) return;
                float r = Mth.clamp(stats.radius(4F + 0.04F * pct), 3F, 12F) * (max ? 1.25F : 1F);
                ElementFx.shockwave(ctx.level(), c.position().add(0, 0.3, 0), max ? PSY_MAX : PSY, r, 16, null);
                for (LivingEntity e : LhUtil.foesAround(ctx, c.position(), r, 10)) lift(ctx, stats, e, pct, max);
                return;
            }
            if (target instanceof LivingEntity le && LhUtil.once(ctx, "psy:" + le.getId())) lift(ctx, stats, le, pct, max);
        }

        /** Emoção do conjurador (0..100). */
        static int emotion(LivingEntity c) {
            if (!(c instanceof Player) || c.getMaxHealth() <= 0) return 50;
            float lost = 1F - c.getHealth() / c.getMaxHealth();
            return Mth.clamp(Math.round(10 + 90 * lost), 10, 100);
        }

        private static void announce(SpellContext ctx, SpellStats stats, LivingEntity c, int pct, boolean max) {
            ServerLevel l = ctx.level();
            ElementFx.aura(l, c, Element.ARCANE, max ? PSY_MAX : PSY, 1.2F, max ? 5 : 3, 30);
            LhUtil.actionbar(c, Component.literal(max ? "???%" : pct + "%").withStyle(max ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA,
                    ChatFormatting.BOLD));
            LhUtil.sound(l, c.position(), SoundEvents.BEACON_ACTIVATE, 1F, max ? 0.6F : 1.4F);
            if (max) {
                LhUtil.banner(c, stats, "psycho_unknown", PSY_MAX, true);
                ElementFx.shake(l, c.position(), 24, 1.0F, 20);
                BossKit.schedule(l, 40, () -> {
                    if (!c.isAlive()) return;
                    c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 1, false, true));
                    c.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0, false, true));
                    LhUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_h.psycho_faint").withStyle(ChatFormatting.GRAY));
                });
            }
        }

        private static void lift(SpellContext ctx, SpellStats stats, LivingEntity e, int pct, boolean max) {
            ServerLevel l = ctx.level();
            boolean boss = LhUtil.boss(e);
            int color = max ? PSY_MAX : PSY;
            BlockState below = l.getBlockState(e.blockPosition().below());
            Vec3 p = e.position();
            if (!below.isAir()) {
                l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), p.x, p.y + 0.1, p.z, 16, 0.6, 0.1, 0.6, 0.15);
            }
            ElementFx.aura(l, e, Element.ARCANE, color, 0.9F, 2, 16);
            if (!boss) {
                e.setDeltaMovement(e.getDeltaMovement().x * 0.2, 0.75 + 0.004 * pct, e.getDeltaMovement().z * 0.2);
                e.hurtMarked = true;
                e.resetFallDistance();
            }
            float dmg = (4F + 0.05F * pct) * (max ? 1.4F : 1F) * (boss ? 0.7F : 1F);
            BossKit.schedule(l, 14, () -> {
                if (!e.isAlive() || e.level() != l) return;
                if (!boss) {
                    e.setDeltaMovement(0, -1.6, 0);
                    e.hurtMarked = true;
                }
                e.invulnerableTime = 0;
                ctx.damage(e, dmg, stats, null);
                Vec3 c = LhUtil.center(e);
                l.sendParticles(LhUtil.dust(color, 1.6F), c.x, c.y, c.z, 14, 0.4, 0.5, 0.4, 0);
                BossKit.schedule(l, 4, () -> ElementFx.shockwave(l, e.position().add(0, 0.2, 0), color, 2.5F, 10, null));
                LhUtil.sound(l, c, SoundEvents.ANVIL_LAND, 0.6F, 0.6F);
                LhUtil.sound(l, c, SoundEvents.PLAYER_ATTACK_STRONG, 1F, 0.8F);
            });
        }
    }

    // =====================================================================================
    //  Genki Dama (aumento)
    // =====================================================================================
    /**
     * AUMENTO (Arcano, tier 4, C5, +1,5 s de conjuração). Erga as mãos: a magia junta a energia dos seres vivos em
     * 32 blocos (aliados 3, bichos de estimação 2, animais e aldeões 1; máx. 30) — poder ×(1 + 0,05 por ponto), até
     * ×2,5 — e ganha +50% de área. "Não fere corações puros": a magia só atinge INIMIGOS. Regras em
     * {@link com.renanpontes.isekaimahou.feature.legends_h.Augments}.
     */
    public static AugmentGlyph genkiDama() {
        return new AugmentGlyph("genki_dama", 12, s -> {
            if (s.custom(LhIds.GENKI_DAMA) == 0) s.aoe += 1;
            s.enemiesOnly = true;
            s.custom.merge(LhIds.GENKI_DAMA, 1, Integer::sum);
        }).castTicks(30).<AugmentGlyph>element(Element.ARCANE).<AugmentGlyph>tier(4).minCircle(5);
    }

    // =====================================================================================
    //  Fala Amaldiçoada
    // =====================================================================================
    /**
     * EFEITO (Sombra, tier 3, C3). Palavras com energia amaldiçoada. Normal: "NÃO SE MEXA!" — o alvo fica atordoado
     * 1,5 s (Estender +; chefes 1/3, jogadores até 1,5 s). Com Amplificar: "EXPLODA!" — 6 de dano e um empurrão. O
     * preço é a garganta: depois de falar você fica Silenciado (1,5 s; 3 s no "Exploda") e perde meio coração (nunca
     * mata). Em você (Si Mesmo): todos os inimigos em 8 blocos (Expandir +; até 8) ouvem.
     */
    public static class CursedSpeech extends EffectGlyph {
        public CursedSpeech() {
            super("cursed_speech", 14);
            element(Element.SHADOW);
            tier(3);
            minCircle(3);
        }

        @Override
        public float baseDamage() { return 6F; }

        @Override
        public int hintDuration() { return 30; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            boolean blast = stats.amplify > 0;
            if (c != null && LhUtil.once(ctx, "speech_cost")) speak(ctx, stats, c, blast);
            if (LhUtil.self(ctx, target)) {
                if (c == null || !LhUtil.once(ctx, "speech_self")) return;
                float r = Mth.clamp(stats.radius(8F), 4F, 16F);
                ElementFx.shockwave(ctx.level(), c.getEyePosition(), CURSE, r, 14, null);
                for (LivingEntity e : LhUtil.foesAround(ctx, c.position(), r, 8)) hear(ctx, stats, c, e, blast);
                return;
            }
            if (target instanceof LivingEntity le && LhUtil.once(ctx, "speech:" + le.getId())) hear(ctx, stats, c, le, blast);
        }

        private static void speak(SpellContext ctx, SpellStats stats, LivingEntity c, boolean blast) {
            ServerLevel l = ctx.level();
            Vec3 mouth = c.getEyePosition().add(c.getLookAngle().scale(0.4)).add(0, -0.15, 0);
            for (int i = 0; i < 3; i++) {
                Vec3 at = mouth.add(c.getLookAngle().scale(1.2 * i));
                final float size = 0.8F + 0.6F * i;
                BossKit.schedule(l, i * 2, () -> ElementFx.shockwave(l, at, CURSE, size, 8, c.getLookAngle()));
            }
            l.sendParticles(LhUtil.dust(CURSE, 1.0F), mouth.x, mouth.y, mouth.z, 6, 0.08, 0.05, 0.08, 0);
            LhUtil.sound(l, mouth, blast ? SoundEvents.WARDEN_SONIC_CHARGE : SoundEvents.EVOKER_PREPARE_ATTACK, 0.8F, blast ? 1.4F : 0.6F);
            LhUtil.banner(c, stats, blast ? "cursed_blast" : "cursed_stop", CURSE, false);
            // A garganta paga
            Statuses.apply(c, Statuses.Kind.SILENCED, 1, blast ? 60 : 30, c, 1F);
            LhUtil.strain(c, blast ? 2F : 1F);
        }

        private static void hear(SpellContext ctx, SpellStats stats, LivingEntity c, LivingEntity e, boolean blast) {
            ServerLevel l = ctx.level();
            Vec3 p = LhUtil.center(e);
            if (blast) {
                ctx.damage(e, 6F, stats, null, Element.SHADOW);
                Vec3 out = c != null ? e.position().subtract(c.position()) : ctx.look();
                out = out.lengthSqr() < 1.0E-4 ? ctx.look() : out.normalize();
                if (!LhUtil.boss(e)) {
                    e.push(out.x * 1.1, 0.35, out.z * 1.1);
                    e.hurtMarked = true;
                }
                ElementFx.flash(l, p, 0xD080FF, 1.8F);
                l.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                LhUtil.sound(l, p, SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 1.6F);
            } else {
                int ticks = Mth.clamp(stats.duration(30), 10, 80);
                if (LhUtil.boss(e)) ticks = Math.max(8, ticks / 3);
                else if (e instanceof Player) ticks = Math.min(ticks, 30);
                Statuses.apply(e, Statuses.Kind.STUNNED, 1, ticks, c, 1F);
                if (e instanceof net.minecraft.world.entity.Mob m) m.getNavigation().stop();
                l.sendParticles(LhUtil.dust(CURSE, 1.3F), p.x, p.y + e.getBbHeight() * 0.4, p.z, 10, 0.3, 0.2, 0.3, 0);
                LhUtil.sound(l, p, SoundEvents.CHAIN_PLACE, 1F, 0.5F);
            }
            if (e instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable(blast ? "message.isekai_mahou.legends_h.heard_blast"
                        : "message.isekai_mahou.legends_h.heard_stop").withStyle(ChatFormatting.DARK_PURPLE), true);
            }
        }
    }

    private OtherGlyphs() {}
}
