package com.renanpontes.isekaimahou.feature.class_rogue.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueCombat;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueFx;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Apunhalar (origem Ladino, Sombra): dano 6; PELAS COSTAS (ou com o alvo distraído, em Emboscada, invisível) ×2,5 e
 * 2 de Sangramento. Contra jogadores ×1,5 e chefes ×1,6 pelas costas. Junte com Golpe, Passo Sombrio ou Leque.
 */
public class BackstabEffect extends EffectGlyph {
    public static final float DAMAGE = 6.0F;

    public BackstabEffect() {
        super("rogue_backstab", 10);
        origin(MagicPath.ROGUE);
        element(Element.SHADOW);
        tier(1);
    }

    @Override
    public float baseDamage() {
        return DAMAGE;
    }

    @Override
    public int hintDuration() {
        return 100;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        LivingEntity caster = ctx.caster();
        if (!(target instanceof LivingEntity le) || caster == null || le == caster) return;
        ServerLevel level = ctx.level();
        boolean back = RogueCombat.exposedBack(le, caster);
        float mult = back ? RogueCombat.backMultiplier(le, RogueCombat.BACKSTAB_MULT) : 1.0F;
        ctx.damage(le, DAMAGE * mult, stats, null, Element.SHADOW);
        if (!back) {
            RogueFx.slash(level, le, 0x9A9AB0);
            RogueFx.sound(level, le.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 1.3F);
            return;
        }
        RogueCombat.consumeAmbush(caster);
        Statuses.applyUpTo(le, Statuses.Kind.BLEEDING, 2, stats.duration(100), caster, stats.power() * ctx.powerMult(), 6);
        RogueFx.slash(level, le, RogueFx.BLOOD);
        RogueFx.crit(level, le, false);
        RogueFx.sound(level, le.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.7F);
        RogueFx.sound(level, le.position(), SoundEvents.TRIDENT_HIT, 0.6F, 1.5F);
        if (caster instanceof ServerPlayer sp) {
            MagicApi.addCounter(sp, "rogue.backstab", 1);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.class_rogue.backstab").withColor(0xB06BFF), true);
        }
    }
}
