package com.renanpontes.isekaimahou.feature.ultimates.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.ultimates.UltFx;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Chuva de Meteoros (ultimate, slot G): o céu escurece e avermelha para quem está perto, um círculo rúnico gigante se
 * abre no alto e, por 15 s, 10 + 4·Círculo rochas em brasa despencam numa área de raio 16 (+4 por Expandir) em volta
 * do ponto mirado — cada uma com os efeitos da magia e sua própria cratera. Parte das rochas mira inimigos (mais com
 * Teleguiado). A chuva fica na Pilha enquanto dura: uma Contramágica forte o bastante faz o resto parar de cair.
 */
public class MeteorShowerForm extends FormGlyph {
    public static final double RANGE = 56.0;

    public MeteorShowerForm() {
        super("meteor_shower", 95);
        element(Element.ARCANE);
        tier(4);
        minCircle(4);
    }

    @Override
    public boolean isUltimate() { return true; }

    @Override
    public int baseCastTicks() { return 30; }

    @Override
    public int baseCooldown() { return 400; }

    @Override
    public void cast(SpellContext ctx) {
        if (!UltFx.casterReady(ctx)) return;
        SpellStats s = ctx.formStats();
        Vec3 center = UltFx.aimGround(ctx, RANGE);
        float radius = Mth.clamp(16F + 4F * Math.max(0, s.aoe), 10F, 40F);
        int count = Mth.clamp(10 + 4 * UltFx.circleOf(ctx.caster()), 10, 60);
        int duration = Mth.clamp(s.duration(300), 200, 600);
        new MeteorShower(ctx, center, radius, count, duration).start();
    }
}
