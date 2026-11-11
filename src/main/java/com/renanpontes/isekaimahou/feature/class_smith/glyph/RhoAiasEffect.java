package com.renanpontes.isekaimahou.feature.class_smith.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.feature.class_smith.entity.RhoAiasEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * "Rho Aias — os Sete Anéis" (homenagem ao escudo de sete pétalas; Terra, benéfico, origem do Ferreiro): ergue na
 * frente do alvo (você com Si Mesmo, ou aliados) um escudo de 7 pétalas de luz que acompanha o olhar por 8 s
 * (Estender +). Cada pétala segura 10 de dano (× poder) vindo da FRENTE — golpes, flechas, magias, explosões — e
 * se parte; projéteis inimigos param no ar ao tocar o escudo. Reaplicar renova as 7 pétalas.
 */
public class RhoAiasEffect extends EffectGlyph {
    public static final float PETAL_HP = 10.0F;
    public static final int TICKS = 160;
    public static final int COLOR = 0xFF8AD0;

    public RhoAiasEffect() {
        super("smith_rho_aias", 22);
        element(Element.EARTH);
        origin(MagicPath.SMITH);
        tier(3);
        minCircle(3);
    }

    @Override
    public boolean isBeneficial() { return true; }

    @Override
    public int hintDuration() { return TICKS; }

    @Override
    public float hintRadius() { return (float) RhoAiasEntity.DISK; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        float hp = PETAL_HP * stats.power() * ctx.powerMult() * Progression.DAMAGE_SCALE;
        int color = COLOR;
        RhoAiasEntity.raise(ctx.level(), le, ctx, hp, stats.duration(TICKS), color);
        if (!stats.silent && ctx.caster() != null) {
            Announce.send(ctx.caster(), Component.translatable("announce.isekai_mahou.smith_rho_aias"), color, Announce.STYLE_SPELL);
        }
    }
}
