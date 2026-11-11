package com.renanpontes.isekaimahou.feature.class_rogue.glyph;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueStolenBook;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Página Roubada (forma de origem do Ladino): solta a magia da página escolhida do Grimório Roubado (gasta 1 carga e
 * METADE do mana original, com 90% do poder +5% por rank; Amplificar nesta forma soma no poder). Efeitos montados
 * depois dela acertam o inimigo na mira na mesma hora (ex.: Página Roubada + Ponto Vital). Sem página: nada sai.
 * Só jogadores. Ignora o Dividir (1 cópia).
 */
public class StolenPageForm extends FormGlyph {
    public StolenPageForm() {
        super("rogue_stolen_page", 6);
        origin(MagicPath.ROGUE);
        tier(3);
        minCircle(2);
    }

    @Override
    public boolean requiresEffect() {
        return false;
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return 1F;
    }

    @Override
    public int baseCooldown() {
        return 20;
    }

    @Override
    public float hintRange() {
        return 20F;
    }

    @Override
    public void cast(SpellContext ctx) {
        if (!(ctx.caster() instanceof ServerPlayer sp) || !sp.isAlive()) return;
        float power = ctx.formStats().power() * ctx.powerMult();
        if (!RogueStolenBook.castSelected(sp, power)) return;
        LivingEntity target = ctx.spell().effects().isEmpty() ? null : ShadowStepForm.aimTarget(ctx, sp, 20);
        if (target != null) {
            ctx.resolveOnEntity(target, null);
        } else {
            // sem alvo para os efeitos: o estágio termina na frente do Ladino (ligações "Depois/Ao Terminar" seguem)
            ctx.end(sp.getEyePosition().add(sp.getLookAngle().scale(2)), sp.getLookAngle());
        }
    }
}
