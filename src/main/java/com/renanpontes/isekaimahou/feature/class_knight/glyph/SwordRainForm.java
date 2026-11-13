package com.renanpontes.isekaimahou.feature.class_knight.glyph;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_knight.KnightFx;
import com.renanpontes.isekaimahou.feature.class_knight.KnightFxNet;
import com.renanpontes.isekaimahou.feature.class_knight.KnightPower;
import com.renanpontes.isekaimahou.feature.class_knight.KnightSkyfall;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Chuva de Espadas (forma, tier 3): um círculo de runas se abre no céu sobre o ponto mirado (até 28 blocos) e cópias
 * espectrais da SUA lâmina caem uma a uma num raio de 3,5 (Expandir); cada espada que crava aplica os efeitos em quem
 * estiver a 1,6 bloco. 5 espadas +2 por Dividir (máx. 13) — cada uma é uma cópia no custo. Estágio 2+: cai no ponto
 * do estágio anterior.
 */
public class SwordRainForm extends FormGlyph {
    public static final int MAX_SWORDS = 13;

    public SwordRainForm() {
        super(KnightGlyphs.SWORD_RAIN, 14);
        element(Element.ARCANE);
        origin(MagicPath.KNIGHT);
        tier(3);
        minCircle(3);
    }

    @Override
    public int baseCastTicks() { return 12; }

    @Override
    public int baseCooldown() { return 70; }

    @Override
    public boolean stageable() { return true; }

    @Override
    public float copies(SpellStats stats, int circle) { return swords(stats); }

    static int swords(SpellStats s) {
        return Math.min(MAX_SWORDS, 5 + 2 * Math.max(0, s.split));
    }

    @Override
    public float hintRange() { return 28F; }

    @Override
    public float hintRadius() { return 3.5F; }

    @Override
    public int hintDuration() { return 40; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        Vec3 center = target(ctx, caster);
        float radius = s.radius(3.5F);
        int n = swords(s);
        int rgb = ctx.color() & 0xFFFFFF;
        ItemStack blade = KnightPower.bladeOf(caster);
        float speed = (float) (1.1 * s.speedMult());
        double sky = center.y + 11;
        // círculo de runas no céu (aparece na hora)
        KnightFx.runeCircle(level, new Vec3(center.x, sky + 0.5, center.z), radius + 0.8, rgb, 40, 0F);
        KnightFx.runeCircle(level, center, radius, rgb, 32, 0.4F);
        KnightFx.sound(level, center, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.5F);
        ElementFx.castSound(level, caster, ctx.element());
        for (int i = 0; i < n; i++) {
            int index = i;
            SpellScheduler.schedule(i * 3, () -> {
                if (caster.isRemoved()) return;
                double a = ctx.random().nextDouble() * Mth.TWO_PI;
                double r = index == 0 ? 0 : Math.sqrt(ctx.random().nextDouble()) * radius;
                double x = center.x + Math.cos(a) * r, z = center.z + Math.sin(a) * r;
                double ground = groundAt(level, x, sky, z, center.y - 6);
                Vec3 from = new Vec3(x, sky, z);
                Vec3 land = new Vec3(x, ground, z);
                boolean ok = KnightSkyfall.drop(level, from, ground, speed, blade, 1.6F, rgb,
                        () -> impact(ctx, caster, land, index == n - 1));
                if (!ok) impact(ctx, caster, land, index == n - 1);
            });
        }
    }

    /** Uma espada cravou: efeitos em volta, impacto para os estágios, estilhaços de chão. */
    private static void impact(SpellContext ctx, LivingEntity caster, Vec3 land, boolean last) {
        ServerLevel level = ctx.level();
        List<LivingEntity> hits = level.getEntitiesOfClass(LivingEntity.class, new AABB(land, land).inflate(1.6, 2.0, 1.6),
                e -> e.isAlive() && !e.isSpectator() && e.position().distanceToSqr(land) <= 1.6 * 1.6 + 4);
        boolean any = false;
        for (LivingEntity e : hits) {
            if (e == caster) continue;
            if (!any && !ctx.isAlly(e)) {
                ctx.impact(KnightFx.center(e), new Vec3(0, -1, 0), e);
                any = true;
            }
            e.invulnerableTime = 0;
            ctx.resolveOnEntity(e, caster);
        }
        // estilhaços do chão, onda e faíscas: desenhados no cliente
        KnightFxNet.at(level, KnightFxNet.SWORD_LAND, land, Vec3.ZERO, 1F, ctx.color() & 0xFFFFFF, -1);
        KnightFx.sound(level, land, SoundEvents.TRIDENT_HIT_GROUND, 1.0F, 0.8F + level.random.nextFloat() * 0.3F);
        if (last && !any) ctx.end(land, new Vec3(0, -1, 0));
    }

    /** Ponto mirado: âncora do estágio, o alvo do mob ou o bloco olhado (até 28). */
    private static Vec3 target(SpellContext ctx, LivingEntity caster) {
        if (ctx.anchor() != null) return ctx.anchor();
        if (!ctx.hasOrigin() && caster instanceof Mob mob && mob.getTarget() != null) return mob.getTarget().position();
        Vec3 eye = ctx.eyePos();
        Vec3 end = eye.add(ctx.look().scale(28));
        BlockHitResult hit = ctx.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 p = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : end;
        return new Vec3(p.x, groundAt(ctx.level(), p.x, p.y + 1, p.z, p.y - 24), p.z);
    }

    /** Altura do chão logo abaixo de (x, fromY, z), sem descer além de {@code minY}. */
    static double groundAt(ServerLevel level, double x, double fromY, double z, double minY) {
        BlockHitResult down = level.clip(new ClipContext(new Vec3(x, fromY, z), new Vec3(x, minY, z), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return down.getType() == HitResult.Type.BLOCK ? down.getLocation().y : minY;
    }
}
