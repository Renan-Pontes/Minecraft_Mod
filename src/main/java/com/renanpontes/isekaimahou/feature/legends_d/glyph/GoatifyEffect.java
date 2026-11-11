package com.renanpontes.isekaimahou.feature.legends_d.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.legends_d.LdUtil;
import com.renanpontes.isekaimahou.feature.legends_d.goat.Goatify;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Transmutar em Cabra (Arcano, tier 3, C3): mob comum vira cabra por 20 s (Estender aumenta); se a cabra morrer, o
 * original morre junto (com loot e abate seus). Chefes resistem (Atordoados 1 s). Jogadores: Maldição da Cabra 6 s.
 */
public class GoatifyEffect extends EffectGlyph {
    public GoatifyEffect() {
        super("goatify", 18);
        element(Element.ARCANE);
        tier(3);
        minCircle(3);
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive() || Goatify.isGoat(le)) return;
        if (le instanceof ServerPlayer sp) {
            if (sp.isCreative() || sp.isSpectator()) return;
            Goatify.curse(sp, stats.duration(120));
            return;
        }
        if (le instanceof Mob mob && Goatify.canTransform(mob)) {
            Goatify.transform(ctx.level(), mob, stats.duration(400), ctx.caster());
            return;
        }
        // Resistiu (chefe ou criatura que não pode virar cabra)
        Statuses.apply(le, Statuses.Kind.STUNNED, 1, 20, ctx.caster(), ctx.powerMult());
        LdUtil.send(ctx.level(), ParticleTypes.SMOKE, LdUtil.center(le), 14, le.getBbWidth() * 0.4, 0.02);
        LdUtil.send(ctx.level(), ParticleTypes.WITCH, LdUtil.center(le), 10, le.getBbWidth() * 0.4, 0.05);
        LdUtil.sound(ctx.level(), le.position(), SoundEvents.GOAT_HORN_BREAK, 1.0F, 0.8F);
        LdUtil.actionbar(ctx.caster(), Component.translatable("message.isekai_mahou.legends_d.goat_resist", le.getDisplayName())
                .withStyle(ChatFormatting.GRAY));
    }
}
