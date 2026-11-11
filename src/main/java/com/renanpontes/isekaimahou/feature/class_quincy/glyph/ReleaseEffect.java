package com.renanpontes.isekaimahou.feature.class_quincy.glyph;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_quincy.Quincy;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyFx;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;

/**
 * Devolução: gasta TODO o Reishi guardado (absorções, drenos, ambiente) e devolve como dano — base 3 + 1,2 por carga
 * (até 20 cargas = +24). As cópias da mesma conjuração (Dividir, saraivadas) dividem o mesmo gasto: o Reishi é tirado
 * no primeiro acerto e vale por 1 s. Mobs que conjuram usam só o dano base.
 */
public class ReleaseEffect extends EffectGlyph {
    public static final float PER_CHARGE = 1.2F;
    private record Spent(long until, int charges) {}

    private static final Map<UUID, Spent> SPENT = new HashMap<>();

    public ReleaseEffect() {
        super("quincy_release", 10);
        origin(MagicPath.QUINCY);
        element(Element.LIGHT);
        tier(2);
    }

    @Override
    public float baseDamage() {
        return 3.0F;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        int charges = 0;
        ServerPlayer sp = ctx.casterPlayer();
        if (sp != null) {
            long now = ctx.level().getGameTime();
            Spent s = SPENT.get(sp.getUUID());
            if (s != null && s.until() >= now) {
                charges = s.charges();
            } else {
                charges = Quincy.takeAllReishi(sp);
                SPENT.put(sp.getUUID(), new Spent(now + 20, charges));
                if (charges == 0) sp.displayClientMessage(Quincy.msg("release_empty").withStyle(ChatFormatting.GRAY), true);
            }
        }
        ctx.damage(target, 3.0F + PER_CHARGE * charges, stats, null);
        QuincyFx.burst(ctx.level(), target.position().add(0, target.getBbHeight() / 2, 0), 10 + charges * 2, 0.3 + charges * 0.03);
        QuincyFx.sound(ctx.level(), target.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 0.6F + charges * 0.02F, 1.4F - charges * 0.02F);
    }

    public static void forget(UUID id) {
        SPENT.remove(id);
    }

    public static void clear() {
        SPENT.clear();
    }
}
