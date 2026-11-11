package com.renanpontes.isekaimahou.feature.class_knight.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_knight.RuneEngraving;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;
import com.renanpontes.isekaimahou.glyph.weapon.WeaponStrikes;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Gravar na Lâmina (forma, tier 1): grava a magia INTEIRA na espada da mão principal. Cada golpe principal solta a
 * próxima runa gravada, pagando só uma fração do custo (ver {@code RuneEngraving}). Os espaços dependem da lâmina
 * (qualquer espada 1, Lâmina Rúnica 2–5, Lendárias 4; Juramento IV +1). Sem lâmina: vale só para o próximo golpe.
 * Mobs: carrega os 2 próximos golpes. Dividir não multiplica (1 cópia: cada golpe rúnico paga a sua parte).
 */
public class EngraveForm extends FormGlyph {
    public EngraveForm() {
        super(KnightGlyphs.ENGRAVE, 6);
        element(Element.ARCANE);
        origin(MagicPath.KNIGHT);
        tier(1);
    }

    @Override
    public int baseCastTicks() { return 10; }

    @Override
    public int baseCooldown() { return 40; }

    @Override
    public float copies(SpellStats stats, int circle) { return 1F; }

    @Override
    public float hintRange() { return 3F; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        if (caster instanceof ServerPlayer sp && ctx.isPlayerCast()) {
            RuneEngraving.engrave(sp, ctx);
        } else {
            WeaponStrikes.arm(caster, ctx, 2, 200);
        }
    }
}
