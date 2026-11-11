package com.renanpontes.isekaimahou.feature.legends_d.glyph;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.legends_d.LdUtil;
import com.renanpontes.isekaimahou.feature.legends_d.entity.AtomicNovaEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Atômico (lendário, Arcano, C7) — "EU SOU ATÔMICO". Uma explosão só por conjuração, no primeiro ponto atingido
 * (com Si Mesmo, sai do próprio corpo do conjurador, que flutua). Raio = (14 + 6·Expandir) × poder (Tudo ou Nada e
 * Colossal crescem muito), até 56 blocos. Os aliados ficam numa bolha protetora (ver {@code AtomicBubble}). Ver {@link AtomicNovaEntity}.
 */
public class AtomicEffect extends EffectGlyph {
    /** Uma explosão por conjuração (formas de área chamam o efeito em cada alvo). */
    private static final Map<UUID, Long> LAST = new HashMap<>();

    public AtomicEffect() {
        super("atomic", 70);
        element(Element.ARCANE);
        legendary();
        minCircle(7);
    }

    @Override
    public boolean wantsBlocks() { return true; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        LivingEntity caster = ctx.caster();
        boolean self = caster != null && target == caster;
        start(ctx, self ? LdUtil.center(caster) : LdUtil.center(target), stats, self);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        start(ctx, hit.getLocation().add(0, 0.6, 0), stats, false);
    }

    private static void start(SpellContext ctx, Vec3 center, SpellStats stats, boolean self) {
        ServerLevel sl = ctx.level();
        UUID key = ctx.casterId();
        if (key != null) {
            long now = sl.getGameTime();
            Long last = LAST.get(key);
            if ((last != null && now - last < 20) || AtomicNovaEntity.isCharging(key, now)) return;
            LAST.put(key, now);
        }
        float power = Mth.clamp(stats.power() * ctx.powerMult(), 0.5F, 4.0F);
        float radius = Mth.clamp((14F + 6F * Math.max(0, stats.aoe)) * (0.6F + 0.4F * power), 10F, 56F);
        sl.addFreshEntity(AtomicNovaEntity.create(ctx, center, radius, stats, self));
    }

    public static void clear() {
        LAST.clear();
    }
}
