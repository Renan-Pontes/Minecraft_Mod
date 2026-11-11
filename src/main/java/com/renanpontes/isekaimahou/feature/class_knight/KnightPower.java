package com.renanpontes.isekaimahou.feature.class_knight;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_knight.outfit.KnightArmorItem;
import com.renanpontes.isekaimahou.feature.class_knight.outfit.KnightOutfitTier;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Multiplicadores das técnicas do Cavaleiro (aplicados no dano base dos glifos de origem dele):
 * <ul>
 *   <li>Juramento V (Coroa Rúnica): ×1,15;</li>
 *   <li>Espada Lendária do mesmo elemento da magia na mão: ×1,10;</li>
 *   <li>armadura completa do Cavaleiro grau II+: ×1,05 (×1,10 no IV).</li>
 * </ul>
 */
public final class KnightPower {
    public static float mult(SpellContext ctx) {
        return mult(ctx.caster(), ctx.element());
    }

    public static float mult(@Nullable LivingEntity caster, Element element) {
        if (caster == null) return 1F;
        float m = 1F;
        if (caster instanceof Player p && oath(p) >= 5) m *= 1.15F;
        LegendSword s = KnightSwords.legendIn(caster);
        if (s != null && s.element == element) m *= 1.10F;
        KnightOutfitTier set = KnightArmorItem.fullSet(caster);
        if (set != null && set.number >= 2) m *= set == KnightOutfitTier.RUNEKING ? 1.10F : 1.05F;
        return m;
    }

    public static int oath(Player p) {
        MagicData d = MagicApi.get(p);
        return d == null ? 0 : d.counter(KnightCounters.OATH);
    }

    public static boolean isKnight(@Nullable LivingEntity e) {
        if (!(e instanceof Player p)) return false;
        MagicData d = MagicApi.get(p);
        return d != null && d.path() == MagicPath.KNIGHT;
    }

    /** Lâmina que o conjurador empunha (para as espadas espectrais copiarem); reserva: a Lâmina Rúnica. */
    public static ItemStack bladeOf(@Nullable LivingEntity e) {
        if (e != null && KnightSwords.holdsSword(e)) return e.getMainHandItem();
        return new ItemStack(KnightRegistries.RUNIC_BLADE.get());
    }

    private KnightPower() {}
}
