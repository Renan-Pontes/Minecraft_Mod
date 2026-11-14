package com.renanpontes.isekaimahou.feature.legends_k.glyph;

import com.renanpontes.isekaimahou.core.Element;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Ponto Zero — Gold Experience Requiem (JoJo), Steel Ball. Um contra-ataque de UMA vez: por 20 s (Estender +) o
 * próximo ataque OU técnica vinda de alguém contra você (ou o aliado que recebeu) é ANULADO — dano, projétil, magia,
 * efeito ruim — e quem tentou "volta a zero": a ação é cancelada (Atordoado 2 s; chefes ½ s), perde o alvo e é
 * empurrado. Recarga longa: 60 s desde que foi armado (a magia nem sai, sem gastar mana, enquanto recarrega).
 */
public class ReturnToZeroEffect extends EffectGlyph {
    public ReturnToZeroEffect() {
        super("lk_return_to_zero", 30);
        origin(MagicPath.STEELBALL);
        element(Element.LIGHT);
        tier(5);
        minCircle(6);
    }

    @Override
    public boolean isBeneficial() { return true; }

    @Override
    public float baseDamage() { return 0F; }

    @Override
    public int hintDuration() { return 400; }

    @Override
    public float hintRadius() { return -1F; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        LivingEntity c = ctx.caster();
        if (c == null || !(target instanceof LivingEntity t) || !t.isAlive()) return;
        Guards.armZero(t, stats.duration(400));
        Guards.lockZero(c);
        ServerLevel l = ctx.level();
        Vec3 p = LkUtil.center(t);
        LkFx.ring(l, p, 1.0, LkFx.REQUIEM, 0.4F, 24);
        LkFx.ring(l, p.add(0, 0.5, 0), 0.6, 0xFFFFFF, 0.3F, 14);
        LkUtil.sound(l, p, SoundEvents.BEACON_ACTIVATE, 0.8F, 1.8F);
        LkUtil.say(t, "zero_armed", ChatFormatting.GOLD);
    }
}
