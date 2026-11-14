package com.renanpontes.isekaimahou.feature.legends_k.glyph;

import java.util.ArrayList;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.legends_k.LkFx;
import com.renanpontes.isekaimahou.feature.legends_k.LkUtil;
import com.renanpontes.isekaimahou.feature.legends_k.state.Guards;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Shun Shun Rikka (Bleach — Orihime), do Encantador (a classe de apoio): as três técnicas das "seis flores".
 * <ul>
 *   <li><b>Santen Kesshun</b> — escudo triangular em você ou num aliado: bloqueia os próximos 3 golpes (+1 por
 *       Amplificar) vindos de alguém, por 15 s (Estender +).</li>
 *   <li><b>Sōten Kisshun</b> — "rejeita" o que aconteceu: cura 6, tira todos os efeitos ruins e conserta 8% da
 *       durabilidade da armadura e das mãos.</li>
 *   <li><b>Koten Zanshun</b> — um corte que atravessa UM alvo por conjuração: dano 8, arranca os corações de absorção e
 *       "rejeita" o efeito bom mais forte dele.</li>
 * </ul>
 */
public final class RikkaEffects {

    /** Santen Kesshun (escudo de 3 golpes). */
    public static class SantenKesshun extends EffectGlyph {
        public SantenKesshun() {
            super("lk_santen_kesshun", 14);
            origin(MagicPath.ENCHANTER);
            element(Element.LIGHT);
            tier(2);
            minCircle(2);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public float baseDamage() { return 0F; }

        @Override
        public int hintDuration() { return 300; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity t) || !t.isAlive()) return;
            int charges = 3 + Math.max(0, stats.amplify);
            Guards.shield(t, charges, stats.duration(300));
            ServerLevel l = ctx.level();
            Vec3 face = t.getLookAngle();
            LkFx.triangle(l, LkUtil.center(t).add(new Vec3(face.x, 0, face.z).normalize().scale(0.9)), face, 1.0, LkFx.RIKKA);
            LkUtil.sound(l, t.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.4F);
            LkUtil.say(t, "santen_on", ChatFormatting.GOLD, charges);
        }
    }

    /** Sōten Kisshun ("rejeitar": cura, limpa e conserta). */
    public static class SotenKisshun extends EffectGlyph {
        public SotenKisshun() {
            super("lk_soten_kisshun", 16);
            origin(MagicPath.ENCHANTER);
            element(Element.LIGHT);
            tier(3);
            minCircle(3);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public float baseDamage() { return 0F; }

        @Override
        public float baseHeal() { return 6F; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity t) || !t.isAlive()) return;
            ctx.heal(t, 6F, stats);
            for (MobEffectInstance inst : new ArrayList<>(t.getActiveEffects())) {
                if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) t.removeEffect(inst.getEffect());
            }
            t.clearFire();
            float frac = 0.08F * Math.min(2F, LkUtil.power(ctx, stats));
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack s = t.getItemBySlot(slot);
                if (s.isEmpty() || !s.isDamageableItem() || !s.isDamaged()) continue;
                int fix = Math.max(1, Math.round(s.getMaxDamage() * frac));
                s.setDamageValue(Math.max(0, s.getDamageValue() - fix));
            }
            // a cúpula dourada: dois anéis e um brilho no topo
            ServerLevel l = ctx.level();
            Vec3 p = t.position();
            double r = Math.max(0.8, t.getBbWidth() * 1.2);
            LkFx.ring(l, p.add(0, 0.2, 0), r, LkFx.RIKKA, 0.3F, 20);
            LkFx.ring(l, p.add(0, t.getBbHeight() * 0.7, 0), r * 0.75, LkFx.RIKKA, 0.3F, 16);
            ElementFx.flash(l, p.add(0, t.getBbHeight() + 0.3, 0), LkFx.RIKKA, 1.2F);
            LkUtil.sound(l, p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
        }
    }

    /** Koten Zanshun (um corte que atravessa e rejeita). */
    public static class KotenZanshun extends EffectGlyph {
        public KotenZanshun() {
            super("lk_koten_zanshun", 14);
            origin(MagicPath.ENCHANTER);
            element(Element.LIGHT);
            tier(3);
            minCircle(4);
        }

        @Override
        public float baseDamage() { return 8F; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null || !(target instanceof LivingEntity t) || LkUtil.self(ctx, target)) return;
            if (!LkUtil.once(ctx, "koten")) return; // um corte só por conjuração
            t.setAbsorptionAmount(0F);
            MobEffectInstance best = null;
            for (MobEffectInstance inst : t.getActiveEffects()) {
                if (inst.getEffect().value().getCategory() != MobEffectCategory.BENEFICIAL) continue;
                if (best == null || inst.getAmplifier() > best.getAmplifier()) best = inst;
            }
            if (best != null) t.removeEffect(best.getEffect());
            ctx.damage(t, 8F, stats, c);
            ServerLevel l = ctx.level();
            Vec3 from = c.getEyePosition();
            Vec3 to = LkUtil.center(t);
            Vec3 past = to.add(to.subtract(from).normalize().scale(1.5));
            LkFx.line(l, from, past, LkFx.RIKKA, 0.3F);
            ElementFx.slash(l, to, to.subtract(from).normalize(), Element.LIGHT, LkFx.RIKKA, 1.6F, 2);
            LkUtil.sound(l, to, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.7F);
        }
    }

    private RikkaEffects() {}
}
