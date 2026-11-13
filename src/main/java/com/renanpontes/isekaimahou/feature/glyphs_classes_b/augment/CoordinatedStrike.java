package com.renanpontes.isekaimahou.feature.glyphs_classes_b.augment;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerCompanions;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerMarks;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.GcbFx;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.GcbIds;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.GcbUtil;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.TriggerCaster;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Aumento do Patrulheiro — <b>Ataque Coordenado</b>: quando a magia sai, até 2 companheiros seus por perto (o Lobo e a
 * Águia do Patrulheiro, ou qualquer bicho domesticado seu a até 24 blocos) repetem a MESMA forma, de onde estão, no
 * seu alvo — cada cópia com 50% do poder, uma depois da outra (0,4 s). O alvo é quem você mira (até 32 blocos) ou, sem
 * ninguém na mira, o inimigo marcado mais próximo. Sem companheiro ou sem alvo, nada acontece (a magia sai normal).
 * Só em formas que saem de um ponto; nunca na forma G.
 */
public final class CoordinatedStrike {
    public static final float POWER = 0.5F;
    public static final double PET_RANGE = 24.0;
    public static final double AIM_RANGE = 32.0;
    public static final int MAX_COMPANIONS = 2;

    public static void onPost(SpellCastEvent.Post e) {
        SpellContext ctx = e.getContext();
        if (!(e.getCaster() instanceof ServerPlayer sp) || sp instanceof FakePlayer || !ctx.isPlayerCast()) return;
        if (GcbUtil.maxCustom(ctx.spell(), GcbIds.COORDINATED) <= 0) return;
        if (ctx.spell().form().isUltimate() || !ctx.spell().form().stageable()) return;
        LivingEntity target = TriggerCaster.aimed(sp, AIM_RANGE);
        if (target == null || ctx.isAlly(target)) target = RangerMarks.nearestMarked(sp, AIM_RANGE);
        if (target == null) return;
        List<LivingEntity> pets = companions(sp);
        if (pets.isEmpty()) return;
        ServerLevel sl = sp.serverLevel();
        float power = ctx.powerMult() * POWER;
        int delay = 8;
        for (LivingEntity pet : pets) {
            LivingEntity t = target;
            SpellScheduler.schedule(delay, () -> {
                if (!pet.isAlive() || !t.isAlive() || pet.level() != sl || t.level() != sl) return;
                Vec3 from = pet.getEyePosition();
                Vec3 dir = GcbFx.center(t).subtract(from).normalize();
                Vec3 origin = from.add(dir.scale(pet.getBbWidth() * 0.5 + 0.4));
                // o bicho "late a ordem": anel verde e som
                GcbFx.ring(sl, pet.position().add(0, 0.1, 0), pet.getBbWidth() + 0.3, 10, GcbFx.dust(GcbFx.RANGER, 0.8F));
                sl.sendParticles(GcbFx.dust(0xFFFFFF, 0.6F), origin.x, origin.y, origin.z, 4, 0.1, 0.1, 0.1, 0.0);
                GcbFx.sound(sl, origin, SoundEvents.ARROW_SHOOT, 0.7F, 1.3F);
                SpellContext c = ctx.withOrigin(origin, dir);
                c.setPowerMult(power);
                c.spell().form().cast(c);
            });
            delay += 8;
        }
    }

    /** Companheiros do Patrulheiro (Lobo, Águia) e bichos domesticados do jogador por perto; no máx. 2. */
    private static List<LivingEntity> companions(ServerPlayer sp) {
        List<LivingEntity> out = new ArrayList<>();
        for (String slot : new String[] {RangerCompanions.WOLF, RangerCompanions.EAGLE}) {
            Entity e = RangerCompanions.find(sp, slot);
            if (e instanceof LivingEntity le && le.level() == sp.level() && le.distanceToSqr(sp) <= PET_RANGE * PET_RANGE) out.add(le);
            if (out.size() >= MAX_COMPANIONS) return out;
        }
        for (LivingEntity le : sp.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(sp.blockPosition()).inflate(PET_RANGE),
                e -> e instanceof OwnableEntity own && sp.getUUID().equals(own.getOwnerUUID()) && e.isAlive() && !e.hasPassenger(sp))) {
            if (out.contains(le)) continue;
            out.add(le);
            if (out.size() >= MAX_COMPANIONS) break;
        }
        return out;
    }

    private CoordinatedStrike() {}
}
