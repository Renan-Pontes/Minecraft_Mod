package com.renanpontes.isekaimahou.feature.class_smith.glyph;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_smith.SmithFx;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Forma "Martelada" (origem do Ferreiro): o martelo bate no chão e uma onda de choque corre 7 blocos à frente rachando
 * a terra (1 bloco por tick; Acelerar = mais rápida; Expandir = mais longa). Cada criatura no caminho recebe os efeitos
 * UMA vez e é jogada um pouco para cima. Dividir: mais ondas em leque (25°). Estágio: sai da origem/âncora do estágio.
 * Não quebra blocos (as rachaduras são só partículas).
 */
public class HammerSlamForm extends FormGlyph {
    public static final float BASE_LENGTH = 7.0F;
    public static final double HIT_RADIUS = 1.4;

    public HammerSlamForm() {
        super("smith_hammer_slam", 6);
        origin(MagicPath.SMITH);
        tier(1);
    }

    @Override
    public int baseCastTicks() { return 6; }

    @Override
    public int baseCooldown() { return 30; }

    @Override
    public boolean stageable() { return true; }

    @Override
    public float hintRange() { return BASE_LENGTH; }

    @Override
    public float hintRadius() { return (float) HIT_RADIUS; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel sl = ctx.level();
        if (caster == null) return;
        SpellStats s = ctx.formStats();
        Vec3 look = ctx.look();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 start;
        if (ctx.hasOrigin()) {
            Vec3 a = ctx.anchor() != null ? ctx.anchor() : ctx.eyePos();
            start = SmithFx.ground(sl, a.add(0, 0.5, 0), 6);
        } else {
            start = SmithFx.ground(sl, caster.position().add(flat.scale(1.2)).add(0, 0.5, 0), 3);
            caster.swing(InteractionHand.MAIN_HAND, true);
        }
        float length = s.radius(BASE_LENGTH);
        float step = Math.min(2.0F, 1.0F * s.speedMult());
        int ticks = Math.max(2, (int) Math.ceil(length / step));
        int lines = Math.min(7, s.count());
        SmithFx.sound(sl, start, SoundEvents.ANVIL_LAND, 1.0F, 0.6F);
        SmithFx.sound(sl, start, SoundEvents.GENERIC_EXPLODE.value(), 0.4F, 1.4F);
        SmithFx.forgeSparks(sl, start.add(0, 0.2, 0), SmithFx.MOLTEN, 24);
        SmithFx.groundBurst(sl, start, 24);
        for (int i = 0; i < lines; i++) {
            float yaw = lines == 1 ? 0F : (i - (lines - 1) / 2.0F) * 25.0F;
            Vec3 dir = com.renanpontes.isekaimahou.glyph.CoreGlyphs.rotate(flat, yaw, 0F);
            dir = new Vec3(dir.x, 0, dir.z).normalize();
            wave(ctx, sl, start, dir, step, ticks, i == lines - 1);
        }
    }

    /** Uma onda: agenda um passo por tick; o último passo da última onda avisa o fim do estágio. */
    private static void wave(SpellContext ctx, ServerLevel sl, Vec3 start, Vec3 dir, float step, int ticks, boolean last) {
        Set<UUID> hit = new HashSet<>();
        double[] y = {start.y};
        for (int t = 1; t <= ticks; t++) {
            final int k = t;
            SpellScheduler.schedule(k, () -> {
                Vec3 p = start.add(dir.scale(k * step));
                // acompanha o relevo (sobe degraus de até 2 blocos e desce até 3)
                Vec3 g = SmithFx.ground(sl, new Vec3(p.x, y[0] + 2.0, p.z), 5);
                y[0] = g.y;
                Vec3 at = new Vec3(p.x, y[0], p.z);
                SmithFx.groundBurst(sl, at, 10);
                sl.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.1, at.z, 3, 0.3, 0.05, 0.3, 0.02);
                sl.sendParticles(SmithFx.spark(SmithFx.EMBER), at.x, at.y + 0.3, at.z, 6, 0.35, 0.2, 0.35, 0.05);
                if (k % 2 == 0) SmithFx.sound(sl, at, SoundEvents.STONE_BREAK, 0.6F, 0.7F);
                List<LivingEntity> near = sl.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(HIT_RADIUS, 2.0, HIT_RADIUS),
                        e -> e.isAlive() && e != ctx.caster() && !e.isSpectator());
                for (LivingEntity e : near) {
                    if (!hit.add(e.getUUID())) continue;
                    ctx.resolveOnEntity(e, null);
                    if (!ctx.isAlly(e)) {
                        e.push(dir.x * 0.25, 0.45, dir.z * 0.25);
                        e.hurtMarked = true;
                    }
                    ctx.impact(SmithFx.center(e), dir, e);
                }
                if (k == ticks) {
                    BlockPos below = BlockPos.containing(at.x, at.y - 0.2, at.z);
                    ctx.resolveOnBlock(new BlockHitResult(at, Direction.UP, below, false), null);
                    SmithFx.forgeSparks(sl, at.add(0, 0.2, 0), SmithFx.MOLTEN, 12);
                    if (last) ctx.end(at, dir);
                }
            });
        }
    }
}
