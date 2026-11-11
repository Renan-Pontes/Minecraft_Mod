package com.renanpontes.isekaimahou.feature.earth_wind.glyph.earth;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.earth_wind.EwFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwTasks;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Tremor: a terra treme em volta do alvo (raio 6). Todos os inimigos no chao sofrem dano, saltam e ficam
 * Atordoados; a onda corre pelo chao e a tela de quem esta perto treme. Quem esta voando nao e afetado.
 */
public class TremorEffect extends EffectGlyph {
    public TremorEffect() {
        super("tremor", 25);
        element(Element.EARTH);
        tier(4);
        minCircle(3);
    }

    /** Efeito de area num ponto: um projetil que acerta o chao tambem dispara. */
    @Override
    public boolean wantsBlocks() {
        return true;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        quake(ctx, target.position(), stats);
    }

    @Override
    public void onBlock(SpellContext ctx, BlockHitResult hit, SpellStats stats) {
        quake(ctx, hit.getLocation(), stats);
    }

    private static void quake(SpellContext ctx, Vec3 center, SpellStats stats) {
        if (!EwUtil.once(ctx, "tremor")) return;
        ServerLevel level = ctx.level();
        float radius = stats.radius(6.0F);
        for (LivingEntity e : ctx.enemiesAround(center, radius)) {
            boolean grounded = e.onGround() || EwUtil.groundBelow(level, BlockPos.containing(e.position()).below(), 0) != null;
            if (!grounded) continue;
            ctx.damage(e, 5.0F, stats, null);
            ctx.status(e, CoreRegistries.STUNNED, 50, 0, stats);
            EwUtil.push(e, Vec3.ZERO, 0.35);
        }
        // onda visual se espalhando pelo chao
        EwTasks.repeat(10, age -> {
            double r = radius * (age + 1) / 10.0;
            EwFx.groundRing(level, center, r, (int) Math.max(8, r * 5));
            return true;
        });
        EwFx.sound(level, center, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 0.45F);
        EwFx.sound(level, center, SoundEvents.RAVAGER_STEP, 1.5F, 0.4F);
        EwFx.sound(level, center, SoundEvents.DEEPSLATE_BREAK, 1.5F, 0.5F);
        EwUtil.shake(level, center, radius * 2.5, 1.2F, 25);
    }
}
