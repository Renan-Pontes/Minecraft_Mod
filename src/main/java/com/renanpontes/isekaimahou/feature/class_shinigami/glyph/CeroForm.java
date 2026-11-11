package com.renanpontes.isekaimahou.feature.class_shinigami.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.feature.class_shinigami.HollowForms;
import com.renanpontes.isekaimahou.feature.class_shinigami.Shinigami;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Cero (homenagem): o disparo dos Vazios — depois de carregar 0,7 s, um feixe rubro de núcleo negro sai em linha reta
 * até 32 blocos (+4 por Acelerar), atravessa TODOS os inimigos (dano próprio 4 + efeitos) e explode no fim. Expandir
 * engrossa o feixe; Fantasma atravessa blocos. Com a Máscara do Vazio ou no Vasto Lorde sai com força total; sem ela,
 * o poder do Vazio vaza (60% do poder, dano próprio 2). Pode ser estágio de magia. Aprendido ao domar o Vazio Interior.
 */
public class CeroForm extends FormGlyph {
    public CeroForm() {
        super("shinigami_cero", 16);
        origin(MagicPath.SHINIGAMI);
        element(Element.SHADOW);
        tier(3);
        minCircle(6);
    }

    @Override
    public boolean stageable() {
        return true;
    }

    @Override
    public int baseCastTicks() {
        return 14;
    }

    @Override
    public int baseCooldown() {
        return 60;
    }

    @Override
    public float hintRange() {
        return 32F;
    }

    @Override
    public float hintRadius() {
        return 2.5F;
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        SpellStats s = ctx.formStats();
        boolean masked = HollowForms.masked(caster);
        if (!masked) ctx.setPowerMult(ctx.powerMult() * 0.6F);
        double range = 32 + 4 * Math.max(0, s.speed);
        float width = 1.2F + 0.4F * Math.max(0, s.aoe);
        Vec3 from = ctx.hasOrigin() ? ctx.eyePos() : caster.getEyePosition().add(ctx.look().scale(0.8)).add(0, -0.2, 0);
        HollowForms.fireCero(ctx, caster, from, ctx.look(), range, width, masked ? 4F : 2F, s.ghost);
        if (caster instanceof ServerPlayer sp && !s.silent && ctx.stage() == 0) {
            Announce.send(sp, Shinigami.msg("cero_shout"), HollowForms.CERO_RGB, Announce.STYLE_SPELL);
        }
    }
}
