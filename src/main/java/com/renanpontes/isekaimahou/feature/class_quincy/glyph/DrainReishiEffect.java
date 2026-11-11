package com.renanpontes.isekaimahou.feature.class_quincy.glyph;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_quincy.Quincy;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyFx;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Drenar Reishi: dano de luz (base 2,5) e rouba a energia do alvo — o conjurador recupera 1,5% do Foco máximo
 * (×poder) e guarda 1 de Reishi (no máximo 1 por tick); se o alvo é um jogador inimigo, ele perde 3% da mana máxima.
 */
public class DrainReishiEffect extends EffectGlyph {
    /** Último tick em que cada conjurador guardou Reishi (Dividir/Chuva não enchem tudo de uma vez). */
    private static final Map<UUID, Long> LAST_STORE = new HashMap<>();

    public DrainReishiEffect() {
        super("quincy_drain_reishi", 6);
        origin(MagicPath.QUINCY);
        element(Element.LIGHT);
        tier(1);
    }

    @Override
    public float baseDamage() {
        return 2.5F;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le)) return;
        ctx.damage(target, 2.5F, stats, null);
        ServerPlayer caster = ctx.casterPlayer();
        if (le instanceof ServerPlayer victim && victim != caster) {
            MagicData vd = MagicApi.get(victim);
            vd.setMana(Math.max(0F, vd.mana() - vd.maxMana() * 0.03F * stats.power()));
        }
        if (caster != null) {
            Quincy.gainFocus(caster, 0.015F * stats.power());
            long now = ctx.level().getGameTime();
            Long last = LAST_STORE.get(caster.getUUID());
            if (last == null || last != now) {
                LAST_STORE.put(caster.getUUID(), now);
                Quincy.addReishi(caster, 1);
            }
            Vec3 from = le.position().add(0, le.getBbHeight() * 0.6, 0);
            QuincyFx.stream(ctx.level(), from, caster.position().add(0, 1.1, 0), 4);
        } else {
            ctx.level().sendParticles(QuincyFx.REISHI, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
        }
        QuincyFx.sound(ctx.level(), le.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.4F, 1.9F);
    }

    public static void forget(UUID id) {
        LAST_STORE.remove(id);
    }

    public static void clear() {
        LAST_STORE.clear();
    }
}
