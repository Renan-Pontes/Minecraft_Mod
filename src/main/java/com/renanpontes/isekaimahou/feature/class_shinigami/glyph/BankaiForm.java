package com.renanpontes.isekaimahou.feature.class_shinigami.glyph;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_shinigami.Archetype;
import com.renanpontes.isekaimahou.feature.class_shinigami.BankaiState;
import com.renanpontes.isekaimahou.feature.class_shinigami.Shinigami;
import com.renanpontes.isekaimahou.feature.transform_laws.BodyEffect;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Bankai — v1.1: CORPO de transformação (era a forma G do Shinigami). Vai dentro da Transformação Suprema (tecla G):
 * {@code Transformação Suprema → Bankai [Estender] → efeitos → leis}. A liberação final da zanpakutō por 20 s (Estender
 * no corpo aumenta): Força II, Resistência I, Velocidade II, Salto, espada ×1,6 com a assinatura reforçada e, a cada
 * segundo, um pulso num raio de 10 (+Expandir no corpo) aplica os efeitos da magia e o campo do arquétipo nos inimigos.
 * A receita moldada no espaço "Bankai" continua entrando por cima (Release.beginBankai).
 */
public class BankaiForm extends BodyEffect {
    public static final int DURATION = 400;

    public BankaiForm() {
        super("zanpakuto_bankai", 25);
        origin(MagicPath.SHINIGAMI);
        element(Element.SHADOW);
        tier(5);
        minCircle(8);
    }

    @Override
    public int bodyTicks(SpellStats stats) {
        return stats.duration(DURATION);
    }

    @Override
    public int minCooldown() {
        return 900;
    }

    @Override
    public boolean supreme() {
        return true;
    }

    @Override
    public String cutsceneStyle() {
        return "spirit";
    }

    @Override
    public int cutsceneRgb() {
        return Shinigami.RGB;
    }

    /** O título da cena é o nome da Bankai do arquétipo (ex.: "Senbonzakura Kageyoshi"). */
    @Nullable
    @Override
    public Component cutsceneTitle(ServerPlayer sp) {
        Archetype a = Shinigami.archetype(MagicApi.get(sp));
        return a == null ? null : a.bankaiName().copy().withColor(a.rgb);
    }

    @Override
    public float hintRadius() {
        return 10F;
    }

    @Override
    public void onTransformEnd(LivingEntity entity) {
        BankaiState.stop(entity);
    }

    @Override
    protected void start(SpellContext ctx, LivingEntity self, SpellStats stats, int ticks) {
        BankaiState.start(ctx, ticks, stats.radius(10F));
    }
}
