package com.renanpontes.isekaimahou.feature.legends_k.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.legends_k.LkUtil;
import com.renanpontes.isekaimahou.feature.legends_k.state.TitanState;
import com.renanpontes.isekaimahou.feature.transform_laws.BodyEffect;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.world.entity.LivingEntity;

/**
 * Modo Titã (Attack on Titan), do Ferreiro — o Titã Martelo de Guerra. É um CORPO de transformação (v1.1: "saírem
 * de formas e virarem efeitos pra forma 'transformação'"): só entra na forma Transformação
 * ({@code Transformação → Modo Titã [Estender] → outros efeitos → leis}). Dura 30 s (Estender/Encurtar mexem). Regras
 * do corpo enorme em {@link TitanState}.
 */
public class TitanModeBody extends BodyEffect {
    public static final int DURATION = 600;

    public TitanModeBody() {
        super("lk_titan_mode", 26);
        origin(MagicPath.SMITH);
        element(Element.EARTH);
        tier(4);
        minCircle(5);
    }

    @Override
    public int bodyTicks(SpellStats stats) {
        return stats.duration(DURATION);
    }

    @Override
    public String cutsceneStyle() {
        return "beast";
    }

    @Override
    public int cutsceneRgb() {
        return 0xFFB060;
    }

    @Override
    public void onTransformEnd(LivingEntity entity) {
        TitanState.stop(entity);
    }

    @Override
    protected void start(SpellContext ctx, LivingEntity self, SpellStats stats, int ticks) {
        TitanState.start(ctx, self, ticks, LkUtil.power(ctx, stats));
    }
}
