package com.renanpontes.isekaimahou.feature.class_gunslinger.glyph;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GgoState;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsFx;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsFxNet;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsNetwork;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsTechniques;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GunCombat;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GunKind;
import com.renanpontes.isekaimahou.feature.class_gunslinger.entity.ManaBulletEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Dois clássicos de pistoleiro de anime (homenagens com nomes próprios): o Dedo Espiritual (o tiro de energia
 * espiritual disparado do dedo indicador) e Duas Mãos (a dança de pistolas duplas que atira em todas as direções).
 */
public final class AnimeGunForms {

    /**
     * Dedo Espiritual: não precisa de arma — você concentra o espírito na ponta do dedo (0,7 s) e dispara uma esfera de
     * energia azul que atravessa 1 alvo e EXPLODE (raio 2,5, ×Expandir). Quanto mais Foco você tiver, mais forte
     * (×1,3 com o recurso vazio até ×2,0 cheio); o tiro gasta mais 10% do Foco atual. Dividir = esferas lado a lado.
     */
    public static class SpiritGunForm extends FormGlyph {
        public static final int COLOR = 0x6FD8FF;
        public static final float BLAST = 2.5F;

        public SpiritGunForm() {
            super("spirit_gun", 12);
            origin(MagicPath.GUNSLINGER);
            tier(3);
            minCircle(2);
        }

        @Override public float copies(SpellStats s, int circle) { return s.count(); }
        @Override public boolean stageable() { return true; }
        @Override public int baseCastTicks() { return 14; }
        @Override public int baseCooldown() { return 60; }
        @Override public float hintRange() { return 80F; }
        @Override public float hintRadius() { return BLAST; }

        @Override
        public void cast(SpellContext ctx) {
            LivingEntity caster = ctx.caster();
            if (caster == null) return;
            ServerLevel level = ctx.level();
            float frac = 0.5F;
            if (caster instanceof ServerPlayer sp) {
                frac = GgoState.focusFraction(sp);
                MagicData d = MagicApi.get(sp);
                if (!sp.getAbilities().instabuild) d.consumeMana(d.mana() * 0.10F);
            }
            float power = (1.3F + 0.7F * frac) * GgoForms.rankPower(caster);
            SpellContext use = ctx.copy(ctx.powerMult() * power);
            SpellStats s = ctx.formStats();
            Vec3 look = ctx.hasOrigin() ? ctx.look() : GgoForms.aim(caster);
            Vec3 from = ctx.hasOrigin() ? ctx.eyePos() : GsFx.muzzle(caster, look, 1);
            int n = Math.max(1, s.count());
            for (int i = 0; i < n; i++) {
                float yaw = n == 1 ? 0 : (i - (n - 1) / 2F) * 7F;
                ManaBulletEntity b = ManaBulletEntity.spell(use, caster, from, ManaBulletEntity.STYLE_SPIRIT);
                b.fire(GunCombat.rotate(look, yaw, 0), 2.2F * s.speedMult(), 0F);
                b.pierce(1).blast(s.radius(BLAST)).lifetime(50);
                level.addFreshEntity(b);
            }
            // o clarão na ponta do dedo
            GsFxNet.at(level, GsFxNet.SPIRIT_GUN, from, look, 0.8F + frac, COLOR, caster.getId());
            GsFx.sound(level, from, SoundEvents.BEACON_ACTIVATE, 0.9F, 1.8F);
            GsFx.sound(level, from, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.0F, 0.5F);
            if (!ctx.hasOrigin()) {
                caster.swing(InteractionHand.MAIN_HAND, true);
                if (caster instanceof ServerPlayer sp) {
                    GsNetwork.anim(sp, GsNetwork.CHARGE, 0, COLOR);
                    GsTechniques.actionbar(sp, GsTechniques.key("spirit_gun"), COLOR);
                }
            }
        }
    }

    /**
     * Duas Mãos: a dança das pistolas duplas — você gira e atira em TODAS as direções, 12 tiros (+4 por Dividir, até
     * 24) em 1,2 s, um de cada mão; cada tiro procura o inimigo mais próximo no setor dele (±25°). Cada bala é uma
     * cópia (55% do poder). Gasta 1 cartucho da arma na mão (se houver).
     */
    public static class TwoHandsForm extends FormGlyph {
        public static final float POWER = 0.55F;
        public static final int COLOR = 0xC08CFF;

        public TwoHandsForm() {
            super("two_hands", 12);
            origin(MagicPath.GUNSLINGER);
            tier(3);
            minCircle(3);
        }

        public static int shots(SpellStats s) {
            return Math.min(24, 12 + 4 * Math.max(0, s.split));
        }

        @Override public float copies(SpellStats s, int circle) { return shots(s); }
        @Override public int baseCooldown() { return 60; }
        @Override public float hintRange() { return 60F; }
        @Override public float hintRadius() { return 20F; }

        @Override
        public void cast(SpellContext ctx) {
            LivingEntity caster = ctx.caster();
            if (caster == null) return;
            SpellContext use = ctx.copy(ctx.powerMult() * POWER * GunCombat.formPower(ctx));
            int n = shots(ctx.formStats());
            float baseYaw = caster.getYRot();
            int span = 24;
            if (caster instanceof ServerPlayer sp) {
                GsNetwork.anim(sp, GsNetwork.FAN, GunKind.DUAL_PISTOLS.ordinal(), COLOR);
                GsTechniques.actionbar(sp, GsTechniques.key("two_hands"), COLOR);
            }
            for (int i = 0; i < n; i++) {
                final int k = i;
                final float yaw = baseYaw + 360F * i / n;
                Runnable r = () -> shot(use, caster, yaw, k);
                int delay = span * i / n;
                if (delay <= 0) r.run();
                else SpellScheduler.schedule(delay, r);
            }
        }

        private static void shot(SpellContext ctx, LivingEntity caster, float yawDeg, int k) {
            if (!caster.isAlive() || caster.isRemoved() || caster.level() != ctx.level()) return;
            ServerLevel level = ctx.level();
            double rad = Math.toRadians(yawDeg);
            Vec3 dir = new Vec3(-Math.sin(rad), -0.05, Math.cos(rad)).normalize();
            Vec3 eye = caster.getEyePosition();
            LivingEntity best = null;
            double bestD = 20 * 20;
            for (LivingEntity e : ctx.enemiesAround(caster.position(), 20)) {
                Vec3 to = e.position().add(0, e.getBbHeight() * 0.6, 0).subtract(eye);
                Vec3 flat = new Vec3(to.x, 0, to.z);
                if (flat.lengthSqr() < 1.0E-4) continue;
                if (flat.normalize().dot(new Vec3(dir.x, 0, dir.z).normalize()) < 0.906) continue; // ±25°
                double d = to.lengthSqr();
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
            if (best != null) dir = best.position().add(0, best.getBbHeight() * 0.6, 0).subtract(eye).normalize();
            int side = k % 2 == 0 ? 1 : -1;
            Vec3 from = eye.add(dir.scale(0.6)).subtract(0, 0.25, 0);
            ManaBulletEntity b = ManaBulletEntity.spell(ctx, caster, from, ManaBulletEntity.STYLE_NORMAL);
            b.fire(dir, 4.0F * ctx.formStats().speedMult(), 1.0F);
            level.addFreshEntity(b);
            GsFx.flash(level, from, dir, COLOR, 0.8F);
            GsFx.sound(level, from, SoundEvents.FIREWORK_ROCKET_BLAST, 0.6F, 1.6F + level.random.nextFloat() * 0.3F);
            caster.swing(side > 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
            if (k % 3 == 0) GsFxNet.at(level, GsFxNet.CHARGED_SHOT, from, dir, 0.6F, COLOR, caster.getId());
        }
    }

    private AnimeGunForms() {}
}
