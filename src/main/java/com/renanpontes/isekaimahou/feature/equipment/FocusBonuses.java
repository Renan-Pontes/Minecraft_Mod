package com.renanpontes.isekaimahou.feature.equipment;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.equipment.item.MagicFocusItem;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

/**
 * Bônus dos Focos Mágicos: multiplica o poder da magia ({@link SpellContext#setPowerMult}) no início da conjuração.
 * Vale para qualquer conjurador com inventário do Curios (jogadores e, se algum dia usarem, mobs).
 */
public final class FocusBonuses {

    public static void onPre(SpellCastEvent.Pre event) {
        LivingEntity caster = event.getCaster();
        SpellContext ctx = event.getContext();
        if (caster == null || ctx == null) return;
        Element element = ctx.element();
        MagicFocusItem best = null;
        float bestBonus = 0F;
        float total = 0F;
        for (SlotResult r : equippedFoci(caster)) {
            MagicFocusItem focus = (MagicFocusItem) r.stack().getItem();
            float b = focus.bonusFor(element);
            if (b <= 0F) continue;
            total += b;
            if (b > bestBonus) { bestBonus = b; best = focus; }
        }
        if (total <= 0F) return;
        ctx.setPowerMult(ctx.powerMult() * (1.0F + total));
        // O foco "responde" à magia: faísca da cor dele saindo do orbe
        Vec3 p = FocusOrb.position(caster, 1.0F);
        ctx.level().sendParticles(ElementFx.dust(best.fxElement(), best.isGreater() ? 1.3F : 0.9F), p.x, p.y, p.z,
                best.isGreater() ? 10 : 6, 0.12, 0.12, 0.12, 0.0);
        if (best.isGreater()) {
            ctx.level().sendParticles(ElementFx.signature(best.fxElement()), p.x, p.y, p.z, 3, 0.1, 0.1, 0.1, 0.01);
        }
    }

    /** Focos equipados de verdade (não cosméticos). */
    public static java.util.List<SlotResult> equippedFoci(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity)
                .map(inv -> inv.findCurios(s -> s.getItem() instanceof MagicFocusItem))
                .orElse(java.util.List.of())
                .stream()
                .filter(r -> !r.slotContext().cosmetic())
                .toList();
    }

    private FocusBonuses() {}
}
