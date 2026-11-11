package com.renanpontes.isekaimahou.feature.legends_d.glyph;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.legends_d.LdUtil;
import com.renanpontes.isekaimahou.feature.legends_d.entity.DisintegrationCageEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Desintegração (lendário, Luz, C6). Em cada alvo: um cilindro de círculos sagrados se fecha (o alvo fica Enraizado)
 * e 1,5 s depois a luz desintegra tudo lá dentro e apaga o terreno até o céu. Num bloco: o cilindro cobre a área
 * (raio 3, Expandir aumenta). Ver {@link DisintegrationCageEntity}.
 */
public class DisintegrationEffect extends EffectGlyph {
    /** Um cilindro por alvo enquanto o anterior não acabou. */
    private static final Map<UUID, Long> CAGED = new HashMap<>();

    public DisintegrationEffect() {
        super("disintegration", 60);
        element(Element.LIGHT);
        legendary();
        minCircle(6);
    }

    @Override
    public boolean wantsBlocks() { return true; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive() || target == ctx.caster()) return;
        ServerLevel sl = ctx.level();
        long now = sl.getGameTime();
        Long until = CAGED.get(le.getUUID());
        if (until != null && now < until) return;
        if (!room(ctx)) return;
        CAGED.put(le.getUUID(), now + DisintegrationCageEntity.CHARGE + 4);
        float r = Mth.clamp(le.getBbWidth() * 0.8F + 1.2F, 1.6F, 4.5F) * (1F + 0.5F * Math.max(0, stats.aoe));
        r = Math.min(r, 9F);
        Vec3 ground = LdUtil.groundBelow(sl, le.position().add(0, 0.2, 0), 12);
        // Alvo voando: a gaiola sobe até acima dele
        float above = (float) (le.getY() - ground.y) + le.getBbHeight() + 2F;
        spawn(ctx, ground, r, Mth.clamp(Math.max(above, Math.max(le.getBbHeight() + 4F, 7F + r)), 6F, 40F), stats);
        Statuses.apply(le, Statuses.Kind.ROOTED, 1, DisintegrationCageEntity.CHARGE + 6, ctx.caster(), ctx.powerMult());
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        ServerLevel sl = ctx.level();
        Vec3 ground = LdUtil.groundBelow(sl, hit.getLocation().add(0, 0.5, 0), 12);
        // Já há um cilindro aqui (o alvo atingido ou outro golpe do mesmo tick)
        if (!sl.getEntitiesOfClass(DisintegrationCageEntity.class, new AABB(ground, ground).inflate(2.5)).isEmpty()) return;
        if (!room(ctx)) return;
        float r = Mth.clamp(stats.radius(3F), 2F, 9F);
        spawn(ctx, ground, r, 8F + r, stats);
    }

    private static boolean room(SpellContext ctx) {
        UUID id = ctx.casterId();
        return id == null || DisintegrationCageEntity.active(id) < DisintegrationCageEntity.MAX_PER_CASTER;
    }

    private static void spawn(SpellContext ctx, Vec3 ground, float r, float height, SpellStats stats) {
        ctx.level().addFreshEntity(DisintegrationCageEntity.create(ctx, ground, r, height, stats));
    }

    /** Limpa marcas velhas (chamado de vez em quando) e ao parar o servidor. */
    public static void prune(long now) {
        CAGED.values().removeIf(t -> t < now);
    }

    public static void clear() {
        CAGED.clear();
    }
}
