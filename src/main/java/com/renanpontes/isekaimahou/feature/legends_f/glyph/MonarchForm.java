package com.renanpontes.isekaimahou.feature.legends_f.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.legends_f.LfUtil;
import com.renanpontes.isekaimahou.feature.legends_f.necro.MonarchState;
import com.renanpontes.isekaimahou.feature.transform_laws.BodyEffect;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Monarca das Sombras (homenagem a Solo Leveling) — v1.1: CORPO de transformação (era a forma ULTIMATE do Necromante).
 * Vai dentro da Transformação Suprema (tecla G): {@code Transformação Suprema → Monarca das Sombras → efeitos → leis}.
 * Por 30 s (Estender no corpo aumenta, até 60 s) TODO o exército sai da sombra de graça — até quem estava se
 * refazendo —, os soldados ficam ×1,5 mais fortes e resistentes, você ganha a armadura de sombras do Monarca, inimigos
 * por perto sentem medo, os efeitos da receita pulsam a cada 2 s nos inimigos a até 8 blocos e toda criatura que morrer
 * a até 24 blocos é erguida na hora. Só jogadores (o exército é do jogador).
 */
public class MonarchForm extends BodyEffect {
    public MonarchForm() {
        super("necro_monarch", 30);
        element(Element.SHADOW);
        origin(MagicPath.NECROMANCER);
        legendary();
        minCircle(8);
    }

    @Override
    public int bodyTicks(SpellStats stats) {
        return Mth.clamp(stats.duration(600), 300, 1200);
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
        return "dark";
    }

    @Override
    public int cutsceneRgb() {
        return MonarchState.COLOR;
    }

    @Override
    public float hintRadius() {
        return (float) MonarchState.PULSE_RADIUS;
    }

    @Override
    public void onTransformEnd(LivingEntity entity) {
        if (entity instanceof ServerPlayer sp) MonarchState.stop(sp);
    }

    @Override
    protected void start(SpellContext ctx, LivingEntity self, SpellStats stats, int ticks) {
        if (!(self instanceof ServerPlayer sp) || LfUtil.isFake(sp) || !sp.isAlive() || sp.level() != ctx.level()) return;
        if (MonarchState.active(sp)) return;
        MonarchState.start(sp, ctx, ticks);
    }
}
