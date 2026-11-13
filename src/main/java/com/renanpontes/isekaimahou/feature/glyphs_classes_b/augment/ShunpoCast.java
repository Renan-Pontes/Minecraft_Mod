package com.renanpontes.isekaimahou.feature.glyphs_classes_b.augment;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.GcbFx;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.GcbIds;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.GcbUtil;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.TriggerCaster;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Aumento do Shinigami — <b>Shunpo: Conjuração Relâmpago</b>: um instante antes de a magia sair, você some num Shunpo e
 * reaparece nas COSTAS do inimigo que está mirando (até 12 blocos, +4 por cópia extra), já virado para ele — a magia
 * sai de lá, à queima-roupa. Sem alvo na mira, o passo vai 6 blocos para a frente (para antes de paredes). Só em
 * conjuração de jogador; um Shunpo por segundo (Canalizar não fica teleportando a cada pulso).
 */
public final class ShunpoCast {
    public static final double RANGE = 12.0;
    public static final double RANGE_PER_COPY = 4.0;
    public static final double FREE_STEP = 6.0;
    private static final int ICD = 20;

    private static final Map<UUID, Long> LAST = new HashMap<>();

    public static void onPre(SpellCastEvent.Pre e) {
        SpellContext ctx = e.getContext();
        if (!(e.getCaster() instanceof ServerPlayer sp) || sp instanceof FakePlayer || !ctx.isPlayerCast() || ctx.hasOrigin()) return;
        int copies = GcbUtil.maxCustom(ctx.spell(), GcbIds.SHUNPO_CAST);
        if (copies <= 0 || sp.isPassenger() || sp.isSleeping()) return;
        ServerLevel sl = sp.serverLevel();
        long now = sl.getGameTime();
        Long last = LAST.get(sp.getUUID());
        if (last != null && now - last < ICD) return;
        double range = RANGE + RANGE_PER_COPY * (copies - 1);

        LivingEntity target = TriggerCaster.aimed(sp, range);
        if (target != null && ctx.isAlly(target)) target = null;
        Vec3 from = sp.position();
        Vec3 dest = target != null ? behind(sp, target) : forward(sp);
        if (dest == null || dest.distanceToSqr(from) < 1.0) return;
        LAST.put(sp.getUUID(), now);

        // pós-imagem no ponto de partida
        sl.sendParticles(GcbFx.dust(0x101014, 1.4F), from.x, from.y + 1.0, from.z, 14, 0.25, 0.5, 0.25, 0.0);
        sl.sendParticles(GcbFx.dust(0xF0F0F0, 1.0F), from.x, from.y + 1.0, from.z, 6, 0.2, 0.5, 0.2, 0.0);
        GcbFx.sound(sl, from, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 1.8F);

        float yaw = sp.getYRot(), pitch = sp.getXRot();
        if (target != null) {
            Vec3 to = GcbFx.center(target).subtract(dest.add(0, sp.getEyeHeight(), 0));
            double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
            yaw = (float) (Math.toDegrees(Math.atan2(to.z, to.x))) - 90.0F;
            pitch = (float) -Math.toDegrees(Math.atan2(to.y, horiz));
        }
        sp.teleportTo(sl, dest.x, dest.y, dest.z, yaw, pitch);
        sp.setYHeadRot(yaw);
        sp.fallDistance = 0;
        sl.sendParticles(ParticleTypes.CLOUD, dest.x, dest.y + 0.2, dest.z, 6, 0.3, 0.05, 0.3, 0.02);
        GcbFx.ring(sl, dest.add(0, 0.1, 0), 0.8, 10, GcbFx.dust(GcbFx.SHINIGAMI, 0.8F));
        GcbFx.sound(sl, dest, SoundEvents.ENDERMAN_TELEPORT, 0.35F, 1.9F);
    }

    /** Ponto nas costas do alvo (do lado oposto a você); se estiver bloqueado, tenta os lados e depois a frente. */
    @Nullable
    private static Vec3 behind(ServerPlayer sp, LivingEntity t) {
        Vec3 d = t.position().subtract(sp.position()).multiply(1, 0, 1);
        if (d.lengthSqr() < 1.0E-4) d = sp.getLookAngle().multiply(1, 0, 1);
        d = d.normalize();
        double off = t.getBbWidth() * 0.5 + sp.getBbWidth() * 0.5 + 0.9;
        Vec3 side = new Vec3(-d.z, 0, d.x);
        Vec3[] options = {t.position().add(d.scale(off)), t.position().add(side.scale(off)), t.position().subtract(side.scale(off)),
                t.position().subtract(d.scale(off))};
        for (Vec3 o : options) {
            Vec3 fixed = fits(sp, o);
            if (fixed != null) return fixed;
        }
        return null;
    }

    /** Sem alvo: 6 blocos para a frente (no plano), parando antes de paredes. */
    @Nullable
    private static Vec3 forward(ServerPlayer sp) {
        Vec3 look = sp.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-4) return null;
        look = look.normalize();
        Vec3 start = sp.position().add(0, 0.5, 0);
        Vec3 end = start.add(look.scale(FREE_STEP));
        BlockHitResult hit = sp.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
        double dist = hit.getType() == HitResult.Type.MISS ? FREE_STEP : Math.max(0, hit.getLocation().distanceTo(start) - 0.6);
        for (double k = dist; k >= 1.0; k -= 0.5) {
            Vec3 fixed = fits(sp, sp.position().add(look.scale(k)));
            if (fixed != null) return fixed;
        }
        return null;
    }

    /** O jogador cabe ali (ou meio bloco acima)? */
    @Nullable
    private static Vec3 fits(ServerPlayer sp, Vec3 pos) {
        for (double dy : new double[] {0.0, 0.5, 1.0}) {
            Vec3 p = pos.add(0, dy, 0);
            AABB box = sp.getBoundingBox().move(p.subtract(sp.position()));
            if (sp.level().noCollision(sp, box)) return p;
        }
        return null;
    }

    public static void forget(UUID id) {
        LAST.remove(id);
    }

    public static void clear() {
        LAST.clear();
    }

    private ShunpoCast() {}
}
