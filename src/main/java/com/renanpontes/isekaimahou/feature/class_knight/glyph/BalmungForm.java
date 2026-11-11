package com.renanpontes.isekaimahou.feature.class_knight.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.class_knight.KnightFx;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;

/**
 * Forma "Balmung — Queda do Crepúsculo" (homenagem a Gram/Balmung, a espada do herói que matou o dragão; Luz, origem do
 * Cavaleiro, Círculo 5): a lâmina libera o brilho do crepúsculo num LEQUE de 140° e 12 blocos (Expandir +) que varre o
 * campo em 5 ondas: todo inimigo no leque recebe os efeitos uma vez. MATADORA DE DRAGÕES: dragões, serpes e wyverns
 * levam os efeitos com 175% do poder; chefes, 135%. Dividir = mais varreduras (0,4 s depois cada). Pode ser estágio.
 */
public class BalmungForm extends FormGlyph {
    public static final float RADIUS = 12.0F;
    public static final float HALF_ANGLE = 70F;
    public static final int AZURE = 0x6FB8FF;
    public static final int TWILIGHT = 0xFFB15A;

    public BalmungForm() {
        super("knight_balmung", 30);
        element(Element.LIGHT);
        origin(MagicPath.KNIGHT);
        tier(4);
        minCircle(5);
    }

    @Override
    public int baseCastTicks() { return 16; }

    @Override
    public int baseCooldown() { return 140; }

    @Override
    public boolean stageable() { return true; }

    @Override
    public float hintRange() { return RADIUS; }

    @Override
    public float hintRadius() { return RADIUS; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel sl = ctx.level();
        if (caster == null) return;
        SpellStats s = ctx.formStats();
        Vec3 origin = ctx.eyePos().subtract(0, 0.5, 0);
        Vec3 dir = KnightFx.flat(ctx.look(), caster.getLookAngle());
        if (!ctx.hasOrigin()) caster.swing(InteractionHand.MAIN_HAND, true);
        float r = s.radius(RADIUS);
        int sweeps = Math.min(4, s.count());
        for (int i = 0; i < sweeps; i++) {
            final boolean last = i == sweeps - 1;
            SpellScheduler.schedule(i * 8, () -> sweep(ctx, sl, origin, dir, r, last));
        }
        KnightFx.pillar(sl, caster.position(), 5, 0.6, TWILIGHT);
        KnightFx.sound(sl, origin, SoundEvents.ENDER_DRAGON_GROWL, 0.5F, 1.8F);
        KnightFx.sound(sl, origin, SoundEvents.BEACON_POWER_SELECT, 1.2F, 0.6F);
        if (!s.silent) Announce.send(caster, Component.translatable("announce.isekai_mahou.knight_balmung"), TWILIGHT, Announce.STYLE_ULTIMATE);
    }

    /** Uma varredura: 5 ondas de meia-lua abrindo e os efeitos em todo inimigo do leque. */
    private static void sweep(SpellContext ctx, ServerLevel sl, Vec3 origin, Vec3 dir, float r, boolean last) {
        LivingEntity caster = ctx.caster();
        if (caster == null || !caster.isAlive()) return;
        for (int w = 1; w <= 5; w++) {
            final double dist = r * w / 5.0;
            final int wave = w;
            SpellScheduler.schedule(w - 1, () -> {
                arc(sl, origin, dir, dist, wave % 2 == 0 ? AZURE : TWILIGHT);
                if (wave == 1) KnightFx.sound(sl, origin, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.5F);
            });
        }
        double cosMax = Math.cos(Math.toRadians(HALF_ANGLE));
        for (LivingEntity e : ctx.enemiesAround(origin, r)) {
            if (e == caster || ctx.ignores(e)) continue;
            Vec3 to = KnightFx.center(e).subtract(origin);
            Vec3 flat = new Vec3(to.x, 0, to.z);
            if (flat.lengthSqr() > 1.0E-4 && flat.normalize().dot(dir) < cosMax) continue;
            if (Math.abs(to.y) > 5) continue;
            float boost = slayer(e);
            SpellContext use = boost == 1F ? ctx : ctx.copy(ctx.powerMult() * boost);
            use.resolveOnEntity(e, null);
            Vec3 c = KnightFx.center(e);
            if (boost > 1F) {
                sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 20, 0.4, 0.6, 0.4, 0.15);
                KnightFx.sound(sl, c, SoundEvents.ANVIL_LAND, 0.8F, 1.5F);
            }
            sl.sendParticles(KnightFx.dust(TWILIGHT, 1.3F), c.x, c.y, c.z, 10, 0.3, 0.4, 0.3, 0);
            ctx.impact(c, dir, e);
        }
        if (last) ctx.end(origin.add(dir.scale(r)), dir);
        BossKit.shake(sl, origin, (int) r + 6, 0.4F, 6);
    }

    /** Matadora de dragões: ×1,75 em dragões/serpes/wyverns; ×1,35 em chefes. */
    public static float slayer(LivingEntity e) {
        if (e instanceof EnderDragon) return 1.75F;
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        if (path.contains("dragon") || path.contains("wyvern") || path.contains("wyrm") || path.contains("drake")
                || path.contains("serpent")) return 1.75F;
        return Statuses.isBossLike(e) ? 1.35F : 1F;
    }

    /** Meia-lua horizontal de 140° na distância {@code d}. */
    private static void arc(ServerLevel sl, Vec3 origin, Vec3 dir, double d, int rgb) {
        double base = Math.atan2(dir.z, dir.x);
        int n = Math.max(10, (int) (d * 3));
        for (int i = 0; i <= n; i++) {
            double a = base + Math.toRadians(-HALF_ANGLE + 2 * HALF_ANGLE * i / n);
            double y = origin.y + 0.2 * Mth.sin((float) (i * 0.6));
            sl.sendParticles(KnightFx.dust(rgb, 1.4F), origin.x + Math.cos(a) * d, y, origin.z + Math.sin(a) * d, 1, 0, 0.05, 0, 0);
            if (i % 4 == 0) {
                sl.sendParticles(ParticleTypes.END_ROD, origin.x + Math.cos(a) * d, y + 0.3, origin.z + Math.sin(a) * d, 1, 0, 0.05, 0, 0.01);
            }
        }
    }
}
