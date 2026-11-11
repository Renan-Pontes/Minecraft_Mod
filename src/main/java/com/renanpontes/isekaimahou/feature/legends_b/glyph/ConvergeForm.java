package com.renanpontes.isekaimahou.feature.legends_b.glyph;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.legends_b.LbFx;
import com.renanpontes.isekaimahou.feature.legends_b.LbUtil;
import com.renanpontes.isekaimahou.feature.legends_b.entity.ConvergeSphereEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Convergência (forma lendária): dois orbes — da cor do 1º e do 2º efeito da magia — surgem à direita e à
 * esquerda, giram e se fundem à frente durante 1 s; então sai uma esfera enorme e lenta que atravessa blocos e
 * criaturas em linha reta (40–64 blocos), aplicando os efeitos em tudo o que toca e, com a destruição ligada,
 * apagando um TÚNEL de terreno do tamanho da esfera no caminho. A fusão das duas forças dá +25% de poder.
 * Expandir = esfera maior · Acelerar/Retardar = velocidade · Estender = mais alcance · Dividir = esferas em leque.
 */
public class ConvergeForm extends FormGlyph {
    /** Bônus de poder da fusão. */
    public static final float FUSION_POWER = 1.25F;
    public static final int CHARGE_TICKS = 20;
    /** Orbe "atração" (azul) e "repulsão" (vermelho) quando os efeitos são neutros (Arcanos). */
    private static final int BLUE = 0x3A7BFF;
    private static final int RED = 0xFF2A3A;

    public ConvergeForm() {
        super("converge", 40);
        element(Element.ARCANE);
        legendary();
        minCircle(5);
    }

    @Override
    public boolean stageable() { return true; }

    @Override
    public int baseCastTicks() { return 10; }

    @Override
    public int baseCooldown() { return 80; }

    @Override
    public void cast(SpellContext ctx) {
        boolean fromHand = !ctx.hasOrigin();
        if (fromHand && !LbUtil.casterReady(ctx)) return;
        SpellStats s = ctx.formStats();
        float radius = Mth.clamp(s.radius(1.9F), 1.0F, 5.5F);
        float speed = 0.38F * s.speedMult();
        // v0.8: alcance maior (40–64 blocos)
        double range = Mth.clamp(40 + 6 * s.duration + 4 * Math.max(0, s.speed), 32, 64);
        int[] colors = orbColors(ctx);
        int n = Math.min(3, s.count());
        // A fusão é uma instância própria (com o bônus de poder); as esferas do leque dividem os disparos de ligação
        SpellContext fused = ctx.copy(ctx.powerMult() * FUSION_POWER);
        LivingEntity caster = ctx.caster();
        Vec3 base = LbUtil.aimDir(ctx);
        for (int i = 0; i < n; i++) {
            float yaw = n == 1 ? 0F : (i - (n - 1) / 2F) * 9F;
            Vec3 dir = base.yRot((float) Math.toRadians(yaw));
            Vec3 pos = fromHand ? ConvergeSphereEntity.holdPoint(caster, dir, 1F) : ctx.eyePos();
            ConvergeSphereEntity e = ConvergeSphereEntity.create(fused, pos, dir, fromHand, yaw, CHARGE_TICKS,
                    radius, speed, range, colors[0], colors[1]);
            ctx.level().addFreshEntity(e);
        }
        if (fromHand && caster != null) {
            LbFx.send(ctx.level(), LbFx.dust(colors[0], 1.2F), caster.getEyePosition(), 6, 0.6, 0);
            LbFx.send(ctx.level(), LbFx.dust(colors[1], 1.2F), caster.getEyePosition(), 6, 0.6, 0);
        }
    }

    /**
     * Cores dos dois orbes: o elemento do 1º e do 2º efeito. Efeitos Arcanos viram o par clássico
     * azul (atração) / vermelho (repulsão); com um só efeito, o 2º orbe usa outro elemento da magia.
     */
    static int[] orbColors(SpellContext ctx) {
        List<ParsedSpell.EffectEntry> fx = ctx.spell().effects();
        Element a = fx.isEmpty() ? Element.ARCANE : fx.get(0).effect().element();
        Element b = fx.size() > 1 ? fx.get(1).effect().element() : null;
        if (b == null) {
            for (Element e : ctx.spell().elements()) {
                if (e != a && e != Element.ARCANE) {
                    b = e;
                    break;
                }
            }
        }
        int ca = a != Element.ARCANE ? a.color : BLUE;
        int cb = b != null && b != Element.ARCANE && b != a ? b.color : (ca == BLUE ? RED : (ca == RED ? BLUE : RED));
        if (ca == cb) cb = ca == RED ? BLUE : RED;
        return new int[]{ca, cb};
    }
}
