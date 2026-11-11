package com.renanpontes.isekaimahou.feature.class_gunslinger.glyph;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsFx;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsNetwork;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GunCombat;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GunKind;
import com.renanpontes.isekaimahou.feature.class_gunslinger.entity.ManaBulletEntity;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Disparo das formas de arma: cria as balas de magia a partir do contexto, com saraivada (Dividir = balas lado a
 * lado), sequência (Rajada) e o leque do aumento "Leque do Cão" (cada tiro vira 1 + 2×N balas em leque, uma a cada
 * 2 ticks — o "fan the hammer" do faroeste).
 */
public final class GunShots {
    /** Aumento de origem "Leque do Cão" (contador em {@link SpellStats#custom}). */
    public static final ResourceLocation FAN = IsekaiMahou.id("gun_fan_hammer");
    /** Abertura entre as balas do leque (graus). */
    public static final float FAN_STEP = 7.0F;
    /** Ticks entre balas do leque. */
    public static final int FAN_DELAY = 2;

    /** Parâmetros de uma salva. */
    public record Volley(int shots, int shotDelay, int side, float speed, float spread, byte style, int seek, int pierce,
                         float knockback, float power, int perShot) {
        /** Salva comum: as balas lado a lado de cada tiro vêm do Dividir. */
        public Volley(int shots, int shotDelay, int side, float speed, float spread, byte style, int seek, int pierce,
                      float knockback, float power) {
            this(shots, shotDelay, side, speed, spread, style, seek, pierce, knockback, power, -1);
        }
    }

    public static int fan(SpellStats s) {
        return Math.max(0, s.custom(FAN));
    }

    /** Balas por "tiro" com o Leque do Cão. */
    public static int fanBullets(SpellStats s) {
        return 1 + 2 * fan(s);
    }

    /**
     * Dispara {@code v.shots} tiros (um a cada {@code v.shotDelay} ticks); cada tiro sai com {@code 1 + Dividir} balas
     * lado a lado e, com Leque do Cão, vira um leque. A arma na mão gasta um cartucho UMA vez por conjuração.
     */
    public static void fire(SpellContext ctx, Volley v) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        float gunPower = GunCombat.formPower(ctx) * v.power();
        SpellContext use = gunPower > 1.001F || gunPower < 0.999F ? ctx.copy(ctx.powerMult() * gunPower) : ctx;
        SpellStats s = ctx.formStats();
        int side = v.side();
        int fanN = fan(s);
        GunKind kind = GunCombat.heldKind(caster);
        for (int shot = 0; shot < v.shots(); shot++) {
            int delay = shot * v.shotDelay();
            boolean first = shot == 0;
            Runnable r = () -> shotNow(use, caster, s, v, side, kind, first);
            if (delay <= 0) r.run();
            else SpellScheduler.schedule(delay, r);
            // leque: as balas extras do cão saem logo depois de cada tiro
            for (int k = 1; k <= fanN; k++) {
                for (int sign = -1; sign <= 1; sign += 2) {
                    float yaw = sign * FAN_STEP * k;
                    int d = delay + FAN_DELAY * (2 * k - (sign < 0 ? 1 : 0));
                    SpellScheduler.schedule(d, () -> bullet(use, caster, s, v, side, yaw, kind, false));
                }
            }
        }
        if (caster instanceof ServerPlayer sp && !ctx.hasOrigin()) {
            GsNetwork.anim(sp, fanN > 0 ? GsNetwork.FAN : GsNetwork.SHOOT, kind.ordinal(), ctx.color() & 0xFFFFFF);
        }
    }

    private static void shotNow(SpellContext ctx, LivingEntity caster, SpellStats s, Volley v, int side, GunKind kind, boolean loud) {
        int n = v.perShot() > 0 ? v.perShot() : s.count();
        for (int i = 0; i < n; i++) {
            float yaw = n == 1 ? 0 : (i - (n - 1) / 2.0F) * 6.0F;
            bullet(ctx, caster, s, v, side, yaw, kind, loud && i == 0);
        }
    }

    private static void bullet(SpellContext ctx, LivingEntity caster, SpellStats s, Volley v, int side, float yaw, GunKind kind,
                               boolean loud) {
        if (caster.isRemoved() && !ctx.hasOrigin()) return;
        Vec3 look = ctx.hasOrigin() ? ctx.look() : caster.getLookAngle();
        Vec3 dir = GunCombat.rotate(look, yaw, 0);
        Vec3 from = ctx.hasOrigin() ? ctx.eyePos() : GsFx.muzzle(caster, look, side);
        ManaBulletEntity b = ManaBulletEntity.spell(ctx, caster, from, v.style());
        b.fire(dir, v.speed() * s.speedMult(), s.spread ? 6.0F : v.spread());
        if (v.seek() > 0) b.seek(v.seek());
        if (v.pierce() > 0) b.pierce(v.pierce());
        if (v.knockback() > 0) b.knockback(v.knockback());
        ctx.level().addFreshEntity(b);
        int rgb = ctx.color() & 0xFFFFFF;
        if (loud) GsFx.shot(ctx.level(), from, dir, rgb, kind);
        else {
            GsFx.flash(ctx.level(), from, dir, rgb, 0.7F);
            GsFx.sound(ctx.level(), from, net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST, 0.5F, 1.8F);
        }
    }

    private GunShots() {}
}
