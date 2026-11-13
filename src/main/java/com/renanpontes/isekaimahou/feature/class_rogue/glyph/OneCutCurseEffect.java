package com.renanpontes.isekaimahou.feature.class_rogue.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueFx;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueFxNet;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueTechniques;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * "Lâmina Maldita: Um Corte, Uma Morte" (homenagem à espada imperial amaldiçoada da assassina de olhos vermelhos;
 * Sombra, origem do Ladino, Círculo 7). Um corte de 6 deixa no alvo uma maldição que sobe pelo corpo como marcas
 * negras; em 3 s ela chega ao coração: criaturas comuns MORREM (veneno amaldiçoado, sem cura). Chefes perdem 12% da vida
 * máxima e jogadores 30% (até 400, × poder) e murcham — ninguém morre na hora contra eles. Recortar o mesmo alvo não
 * reinicia a contagem.
 */
public class OneCutCurseEffect extends EffectGlyph {
    public static final float DAMAGE = 6.0F;

    public OneCutCurseEffect() {
        super("rogue_one_cut", 40);
        origin(MagicPath.ROGUE);
        element(Element.SHADOW);
        tier(5);
        minCircle(7);
    }

    @Override
    public float baseDamage() { return DAMAGE; }

    @Override
    public int hintDuration() { return RogueTechniques.CURSE_DELAY; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        LivingEntity caster = ctx.caster();
        if (!(target instanceof LivingEntity le) || le == caster || !le.isAlive()) return;
        ServerLevel sl = ctx.level();
        ctx.damage(le, DAMAGE, stats, null);
        Vec3 c = RogueFx.center(le);
        // o corte: um traço vermelho-escuro longo e limpo
        Vec3 side = new Vec3(Math.cos(sl.random.nextDouble() * Math.PI), 0, Math.sin(sl.random.nextDouble() * Math.PI)).normalize();
        RogueFxNet.at(sl, RogueFxNet.ONE_CUT, c, side, 1F, 0x8A0A1E, le.getId());
        RogueFx.sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.6F);
        if (!le.isAlive()) return;
        RogueTechniques.curse(le, ctx, stats);
        if (!stats.silent && caster != null) {
            Announce.send(caster, Component.translatable("announce.isekai_mahou.rogue_one_cut"), RogueFx.BLOOD, Announce.STYLE_SPELL);
        }
    }
}
