package com.renanpontes.isekaimahou.feature.legends_e.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.legends_e.LeFx;
import com.renanpontes.isekaimahou.feature.legends_e.LeRegistry;
import com.renanpontes.isekaimahou.feature.legends_e.LeUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * O pilar da Queda Final, no centro do chão atingido. Fases (ticks): mira 0–30 (círculo no chão e no céu, um fio de luz
 * liga os dois), descida 30–38 (o pilar cai do céu), impacto em 38 (vaporiza, clarão, tremor, terreno vira vidro e
 * some), ativo 38–78 (queima quem entrar), onda de calor nos primeiros 20 ticks do impacto, e some até 96.
 */
public class FallenPillarEntity extends LeSpellEntity {
    public static final int TARGET = 30;
    public static final int DESCEND = 8;
    public static final int IMPACT = TARGET + DESCEND;
    public static final int ACTIVE = 40;
    public static final int FADE = 18;
    public static final int HEAT_TICKS = 20;
    /** Dano "vaporizar" (base, ×poder, fura armadura). */
    private static final float VAPORIZE = 40F;
    private static final float BURN = 6F;
    private static final float HEAT = 10F;
    private static final int MAX_PER_CASTER = 10;

    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(FallenPillarEntity.class, EntityDataSerializers.FLOAT);

    private UUID owner;
    private final Set<UUID> heatHit = new HashSet<>();

    public FallenPillarEntity(EntityType<? extends FallenPillarEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineExtraData(SynchedEntityData.Builder b) {
        b.define(DATA_RADIUS, 5F);
    }

    public float radius() { return entityData.get(DATA_RADIUS); }

    public static void spawn(SpellContext ctx, Vec3 ground, SpellStats stats) {
        ServerLevel level = ctx.level();
        float r = Mth.clamp(stats.radius(4.5F), 3F, 16F);
        if (!LeUtil.claim("fallen_down", level, ctx.casterId(), ground, r * 1.1, 40)) return;
        UUID id = ctx.casterId();
        if (id != null && level.getEntities(LeRegistry.FALLEN_PILLAR.get(), e -> id.equals(e.owner)).size() >= MAX_PER_CASTER) return;
        FallenPillarEntity e = new FallenPillarEntity(LeRegistry.FALLEN_PILLAR.get(), level);
        e.bind(ctx, stats.copy());
        e.owner = id;
        e.entityData.set(DATA_RADIUS, r);
        e.setPos(ground.x, ground.y, ground.z);
        level.addFreshEntity(e);
        Telegraph.show(level, Telegraph.Shape.circle(ground, r).warmup(IMPACT).color(0xBFE6FF));
        LeFx.sound(level, ground, SoundEvents.BEACON_ACTIVATE, 2.5F, 1.4F);
        LeFx.sound(level, ground, SoundEvents.AMETHYST_BLOCK_CHIME, 2.0F, 0.6F);
    }

    @Override
    protected void serverTick(ServerLevel level, SpellContext ctx) {
        float r = radius();
        Vec3 g = position();
        if (age < IMPACT) {
            if (age % 2 == 0) {
                double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * r;
                LeFx.send(level, Vfx.endRod(), g.x + Math.cos(a) * d, g.y + 0.2, g.z + Math.sin(a) * d, 1, 0.05, 0.05, 0.05, 0.08);
            }
            if (age == TARGET) {
                LeFx.sound(level, g, SoundEvents.BEACON_POWER_SELECT, 3.0F, 0.5F);
                LeFx.sound(level, g.add(0, 30, 0), SoundEvents.ELYTRA_FLYING, 3.0F, 1.6F);
            }
            return;
        }
        if (age == IMPACT) impact(level, ctx, g, r);
        int t = age - IMPACT;
        if (t > 0 && t <= HEAT_TICKS) heatWave(level, ctx, g, r, t);
        if (t > 0 && t < ACTIVE && t % 10 == 0) burnInside(level, ctx, g, r);
        if (t > 0 && t < ACTIVE && t % 3 == 0) {
            LeFx.send(level, Vfx.endRod(), g.x, g.y + 1, g.z, 6, r * 0.5, 2.0, r * 0.5, 0.15);
            LeFx.send(level, Vfx.whiteAsh(), g.x, g.y + 3, g.z, 10, r * 0.8, 3.0, r * 0.8, 0.02);
        }
        if (t == ACTIVE) LeFx.sound(level, g, SoundEvents.BEACON_DEACTIVATE, 3.0F, 0.6F);
        if (t >= ACTIVE + FADE) discard();
    }

    private void impact(ServerLevel level, SpellContext ctx, Vec3 g, float r) {
        SpellStats s = stats != null ? stats : new SpellStats();
        LeFx.flash(level, g, 72 + r * 3, 0xE6F2FF, 20, 0.85F);
        BossKit.shake(level, g, 64 + r * 2, 2.6F, 34);
        LeFx.sound(level, g, SoundEvents.LIGHTNING_BOLT_THUNDER, 5.0F, 0.5F);
        LeFx.sound(level, g, SoundEvents.LIGHTNING_BOLT_IMPACT, 4.0F, 0.6F);
        LeFx.sound(level, g, SoundEvents.GENERIC_EXPLODE, 4.0F, 0.6F);
        LeFx.sound(level, g, SoundEvents.BEACON_POWER_SELECT, 3.0F, 1.6F);
        LeFx.ring(level, g.add(0, 0.3, 0), new Vec3(0, 1, 0), r * 3.2F, 0xCFE8FF, HEAT_TICKS + 4);
        LeFx.ring(level, g.add(0, 0.6, 0), new Vec3(0, 1, 0), r * 1.8F, 0xFFFFFF, 14);
        LeFx.send(level, ParticleTypes.FLASH, g.add(0, 1, 0), 3, r * 0.3, 0);
        LeFx.send(level, Vfx.endRod(), g.x, g.y + 1, g.z, 160, r * 0.6, 1.0, r * 0.6, 0.6);
        LeFx.send(level, Vfx.cloud(), g.x, g.y + 0.5, g.z, 60, r, 0.3, r, 0.25);

        // Vaporizar: tudo que a magia afeta dentro do pilar (do fundo até bem alto)
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(g.x - r, g.y - r * 1.6, g.z - r, g.x + r, g.y + 60, g.z + r),
                e -> e.isAlive() && horizontal(e.position(), g) <= r + e.getBbWidth() * 0.5 && LeUtil.affects(ctx, e))) {
            ctx.damage(e, LeUtil.piercingBase(ctx, s, e, VAPORIZE), s, this);
            if (e.isAlive()) Statuses.apply(e, Statuses.Kind.BURNING, 3, 160, ctx.caster(), ctx.powerMult());
            else LeFx.send(level, LeFx.fade(0xFFFFFF, 0x404060, 1.4F), LeUtil.center(e), 30, 0.4, 0.05);
        }

        // Terreno: um poço de paredes de vidro e uma cicatriz queimada em volta (respeita a regra de destruição)
        LivingEntity caster = ctx.caster();
        float depth = r * 1.6F;
        Destruction.surface(level, g, r * 2.6F, caster, Destruction.Mode.SCORCH);
        Destruction.cylinder(level, g.add(0, -depth, 0), r * 1.35F, depth + 3F, caster, Destruction.Mode.VITRIFY);
        Destruction.cylinder(level, g.add(0, -depth + 1, 0), r, depth - 1F + 40F, caster, Destruction.Mode.ERASE);
    }

    /** Onda de calor: uma frente que sai do pilar até 3× o raio, queimando e empurrando quem ela alcança (uma vez cada). */
    private void heatWave(ServerLevel level, SpellContext ctx, Vec3 g, float r, int t) {
        SpellStats s = stats != null ? stats : new SpellStats();
        double front = r + 2.2 * r * (t / (double) HEAT_TICKS);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(g, g).inflate(front, 8, front),
                e -> e.isAlive() && !heatHit.contains(e.getUUID()) && LeUtil.affects(ctx, e))) {
            double d = horizontal(e.position(), g);
            if (d > front || d <= r * 0.9) continue;
            heatHit.add(e.getUUID());
            ctx.damage(e, HEAT * (float) (1.0 - 0.5 * (d - r) / (2.2 * r)), s, this);
            Statuses.apply(e, Statuses.Kind.BURNING, 2, 120, ctx.caster(), ctx.powerMult());
            Vec3 push = e.position().subtract(g).multiply(1, 0, 1);
            if (push.lengthSqr() > 1.0E-4 && !Statuses.isBossLike(e)) {
                push = push.normalize().scale(1.2);
                e.push(push.x, 0.45, push.z);
                e.hurtMarked = true;
            }
        }
        if (t % 2 == 0) {
            for (int i = 0; i < 16; i++) {
                double a = Math.PI * 2 * i / 16 + random.nextDouble() * 0.3;
                LeFx.send(level, Vfx.flame(), g.x + Math.cos(a) * front, g.y + 0.4, g.z + Math.sin(a) * front, 1, 0.2, 0.1, 0.2, 0.02);
            }
        }
    }

    private void burnInside(ServerLevel level, SpellContext ctx, Vec3 g, float r) {
        SpellStats s = stats != null ? stats : new SpellStats();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(g.x - r, g.y - r * 1.6, g.z - r, g.x + r, g.y + 60, g.z + r),
                e -> e.isAlive() && horizontal(e.position(), g) <= r && LeUtil.affects(ctx, e))) {
            ctx.damage(e, BURN, s, this);
            Statuses.apply(e, Statuses.Kind.BURNING, 1, 100, ctx.caster(), ctx.powerMult());
        }
        LeFx.sound(level, g, SoundEvents.FIRE_AMBIENT, 2.0F, 0.6F);
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** 0..1 da descida (0 = pilar ainda no céu, 1 = tocou o chão). Usado pelo renderer. */
    public float descent(float ageF) {
        return Mth.clamp((ageF - TARGET) / DESCEND, 0F, 1F);
    }

    /** 0..1: força do pilar (cresce no impacto, segura, some no fim). */
    public float strength(float ageF) {
        float t = ageF - IMPACT;
        if (t < 0) return 0F;
        if (t < ACTIVE) return 1F;
        return Mth.clamp(1F - (t - ACTIVE) / FADE, 0F, 1F);
    }
}
