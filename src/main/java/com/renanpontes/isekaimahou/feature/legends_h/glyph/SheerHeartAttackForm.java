package com.renanpontes.isekaimahou.feature.legends_h.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.legends_h.LhUtil;
import com.renanpontes.isekaimahou.feature.legends_h.entity.SheerHeartAttackEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * FORMA (Fogo, tier 4, C4) — homenagem ao Sheer Heart Attack. Solta um tanquinho com caveira que anda pelo chão e
 * caça o inimigo MAIS QUENTE em 24 blocos (pegando fogo > criaturas de fogo > jogadores > resto). Ao encostar,
 * explode aplicando os efeitos da magia e continua caçando: 3 explosões (Dividir +1 cada, até 6) ou 15 s
 * (Estender +). Acelerar = anda mais rápido. Indestrutível. Um por conjurador; quieto em Modo Paz e em cidades.
 */
public class SheerHeartAttackForm extends FormGlyph {
    public SheerHeartAttackForm() {
        super("sheer_heart_attack", 22);
        element(Element.FIRE);
        tier(4);
        minCircle(4);
    }

    @Override
    public int baseCooldown() { return 60; }

    @Override
    public boolean isPersistent() { return true; }

    @Override
    public float copies(SpellStats stats, int circle) {
        return contacts(stats);
    }

    @Override
    public int hintDuration() { return 300; }

    @Override
    public float hintRange() { return 24F; }

    static int contacts(SpellStats s) {
        return Mth.clamp(3 + Math.max(0, s.split), 3, 6);
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity c = ctx.caster();
        SpellStats s = ctx.formStats();
        Vec3 at;
        if (ctx.hasOrigin() && ctx.anchor() != null) at = ctx.anchor();
        else if (c != null) at = c.position().add(c.getLookAngle().multiply(1.0, 0, 1.0)).add(0, 0.2, 0);
        else return;
        int life = Mth.clamp(s.duration(300), 100, 800);
        float speed = Mth.clamp(0.28F * s.speedMult(), 0.15F, 0.6F);
        SheerHeartAttackEntity.spawn(ctx, s, at, life, contacts(s), speed);
        if (c != null) LhUtil.banner(c, s, "sheer_heart_attack", SheerHeartAttackEntity.COLOR, false);
    }
}
