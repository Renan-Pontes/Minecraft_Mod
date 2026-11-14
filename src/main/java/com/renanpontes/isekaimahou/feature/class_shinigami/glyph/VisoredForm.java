package com.renanpontes.isekaimahou.feature.class_shinigami.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_shinigami.HollowForms;
import com.renanpontes.isekaimahou.feature.transform_laws.BodyEffect;
import com.renanpontes.isekaimahou.feature.transform_laws.TransformForge;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Máscara do Vazio (homenagem aos Visored) — v1.1: CORPO de transformação (era forma comum). Vai dentro da
 * Transformação (qualquer tecla): {@code Transformação → Máscara do Vazio → efeitos → leis}. A mão passa no rosto e a
 * máscara branca de listras rubras se forma por 20 s (+5 s a cada 10 usos, até 45 s; Estender no corpo aumenta):
 * técnicas Shinigami +30% de poder, Força I, Velocidade I, o Cero com força total, Getsuga Negro e a VOZ DUPLA. Ao pôr a
 * máscara, uma explosão de Reiatsu aplica os efeitos da magia em quem estiver a 4 blocos. Aprendida ao vencer o Vazio
 * Interior no Mundo Interior.
 */
public class VisoredForm extends BodyEffect {
    public static final int DURATION = 400;

    public VisoredForm() {
        super("shinigami_visored", 10);
        origin(MagicPath.SHINIGAMI);
        element(Element.SHADOW);
        tier(4);
        minCircle(6);
    }

    /** Sem o jogador aqui: a base (20 s). O tempo treinado (até 45 s) entra no começo ({@link #start}). */
    @Override
    public int bodyTicks(SpellStats stats) {
        return stats.duration(DURATION);
    }

    @Override
    public int minCooldown() {
        return 600;
    }

    @Override
    public String cutsceneStyle() {
        return "dark";
    }

    @Override
    public int cutsceneRgb() {
        return HollowForms.CERO_RGB;
    }

    @Override
    public float hintRadius() {
        return 4F;
    }

    @Override
    public void onTransformEnd(LivingEntity entity) {
        HollowForms.stopForm(entity);
    }

    @Override
    protected void start(SpellContext ctx, LivingEntity self, SpellStats stats, int ticks) {
        int dur = ticks;
        if (self instanceof ServerPlayer sp) {
            // a máscara treinada dura mais (até 45 s): se ela for o corpo mais longo, a transformação inteira acompanha
            dur = Math.max(ticks, stats.duration(HollowForms.visoredTicks(MagicApi.get(sp))));
            if (dur > TransformForge.castRemaining(sp)) TransformForge.setCastRemaining(sp, dur);
        }
        if (HollowForms.startVisored(ctx, dur)) self.swing(net.minecraft.world.InteractionHand.OFF_HAND, true);
    }
}
