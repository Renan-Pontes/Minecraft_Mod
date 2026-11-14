package com.renanpontes.isekaimahou.feature.class_shinigami.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_shinigami.HollowForms;
import com.renanpontes.isekaimahou.feature.transform_laws.BodyEffect;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.LivingEntity;

/**
 * Vasto Lorde (homenagem à forma Hollow completa do Ichigo) — v1.1: CORPO de transformação (era forma G). Vai dentro da
 * Transformação Suprema (tecla G). Por 25 s (Estender no corpo aumenta) o Vazio toma o corpo: máscara com chifres
 * longos, buraco no peito, Força III, Resistência II, Velocidade II, regeneração (3% da vida por segundo), golpes ×1,5,
 * técnicas Shinigami +50%, um pulso por segundo num raio de 6 (+Expandir no corpo) que aplica os efeitos da magia e um
 * CERO GIGANTE a cada 5 s para onde você olha. ARRISCADA: ao acabar o corpo desaba (vida em 25%, Fraqueza II,
 * Lentidão, Cansaço e o Vigor zerado). Rank V + Vazio domado + 30 usos da Máscara.
 */
public class VastoLordeForm extends BodyEffect {
    public static final int DURATION = 500;

    public VastoLordeForm() {
        super("shinigami_vasto_lorde", 30);
        origin(MagicPath.SHINIGAMI);
        element(Element.SHADOW);
        tier(5);
        minCircle(9);
    }

    @Override
    public int bodyTicks(SpellStats stats) {
        return stats.duration(DURATION);
    }

    @Override
    public int minCooldown() {
        return 1200;
    }

    @Override
    public boolean supreme() {
        return true;
    }

    @Override
    public String cutsceneStyle() {
        return "beast";
    }

    @Override
    public int cutsceneRgb() {
        return HollowForms.CERO_RGB;
    }

    @Override
    public float hintRadius() {
        return 6F;
    }

    @Override
    public void onTransformEnd(LivingEntity entity) {
        HollowForms.stopForm(entity);
    }

    @Override
    protected void start(SpellContext ctx, LivingEntity self, SpellStats stats, int ticks) {
        HollowForms.startVasto(ctx, ticks, stats.radius(6F));
    }
}
