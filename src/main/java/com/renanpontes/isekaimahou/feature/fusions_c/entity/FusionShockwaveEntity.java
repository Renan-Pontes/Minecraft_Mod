package com.renanpontes.isekaimahou.feature.fusions_c.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.fusions_c.FcFx;
import com.renanpontes.isekaimahou.feature.fusions_c.FcRegistries;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

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
import net.minecraft.world.phys.Vec3;

/**
 * Onda de choque das Misturas C: anel no chão + casca de esfera que cresce até {@code scale()} (raio) e some.
 * Tipos: VAPOR (branca, detonação), GELO (Zero Absoluto — congela quem a frente da onda atravessa), SOL (dourada,
 * Supernova), SAGRADA (Purgar), PÓLEN (explosão de pólen). Só a de GELO tem regra de jogo; as outras são visuais.
 */
public class FusionShockwaveEntity extends FcSpellEntity {
    public enum Kind { STEAM, FROST, SOLAR, HOLY, POLLEN }

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(FusionShockwaveEntity.class, EntityDataSerializers.INT);

    /** Alvos que a frente da onda de gelo já atravessou. */
    private final Set<UUID> frozen = new HashSet<>();
    private float frostDamage = 10F;
    private int freezeTicks = 80;

    public FusionShockwaveEntity(EntityType<? extends FusionShockwaveEntity> type, Level level) {
        super(type, level);
    }

    /** Onda só visual. */
    public static FusionShockwaveEntity visual(ServerLevel sl, Vec3 at, Kind kind, float radius, int ticks) {
        FusionShockwaveEntity w = new FusionShockwaveEntity(FcRegistries.FUSION_SHOCKWAVE.get(), sl);
        w.setPos(at.x, at.y, at.z);
        w.setup(null, new SpellStats(), Element.ARCANE, Math.max(6, ticks), radius);
        w.entityData.set(KIND, kind.ordinal());
        sl.addFreshEntity(w);
        return w;
    }

    /** Onda do Zero Absoluto: congela e fere quem a frente atravessa. */
    public static FusionShockwaveEntity frost(SpellContext ctx, SpellStats stats, Vec3 at, float radius, int ticks, float damage, int freezeTicks) {
        ServerLevel sl = ctx.level();
        FusionShockwaveEntity w = new FusionShockwaveEntity(FcRegistries.FUSION_SHOCKWAVE.get(), sl);
        w.setPos(at.x, at.y, at.z);
        w.setup(ctx, stats, Element.ICE, ticks, radius);
        w.entityData.set(KIND, Kind.FROST.ordinal());
        w.frostDamage = damage;
        w.freezeTicks = freezeTicks;
        sl.addFreshEntity(w);
        return w;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(KIND, 0);
    }

    public Kind kind() {
        int k = entityData.get(KIND);
        return Kind.values()[Mth.clamp(k, 0, Kind.values().length - 1)];
    }

    /** Fração de expansão 0..1 (rápida no começo, desacelera — "ease out"). */
    public float progress(float pt) {
        float t = Mth.clamp((age() + pt) / (maxLife() * 0.75F), 0F, 1F);
        return 1F - (1F - t) * (1F - t);
    }

    public float radius(float pt) {
        return scale() * progress(pt);
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        if (kind() != Kind.FROST || ctx == null) return;
        float r = radius(0);
        Vec3 c = position();
        for (LivingEntity le : enemiesIn(c.add(0, 1, 0), r + 1)) {
            if (!frozen.add(le.getUUID())) continue;
            hit(le, frostDamage);
            if (!le.isAlive()) continue;
            Statuses.apply(le, Statuses.Kind.FROZEN, 1, freezeTicks, owner(), power);
            Statuses.apply(le, Statuses.Kind.CHILLED, 3, freezeTicks + 60, owner(), power);
            Statuses.clear(le, Statuses.Kind.BURNING);
            sl.sendParticles(ParticleTypes.SNOWFLAKE, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 16,
                    le.getBbWidth() * 0.5, le.getBbHeight() * 0.4, le.getBbWidth() * 0.5, 0.05);
            FcFx.sound(sl, le.position(), SoundEvents.PLAYER_HURT_FREEZE, 1F, 0.8F);
        }
        // Frente da onda: neve e cristais saindo do chão
        if (age() % 2 == 0 && r > 0.5F) {
            int pts = Math.min(48, (int) (r * 4));
            for (int i = 0; i < pts; i++) {
                double a = Math.PI * 2 * i / pts + random.nextDouble() * 0.1;
                sl.sendParticles(ParticleTypes.SNOWFLAKE, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r, 1, 0.1, 0.2, 0.1, 0.02);
            }
        }
    }

    @Override
    protected void clientTick() {
        if (kind() == Kind.STEAM && age() < 6) {
            float r = radius(0);
            for (int i = 0; i < 6; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                level().addParticle(ParticleTypes.CLOUD, getX() + Math.cos(a) * r, getY() + random.nextDouble() * r * 0.6,
                        getZ() + Math.sin(a) * r, Math.cos(a) * 0.15, 0.05, Math.sin(a) * 0.15);
            }
        }
    }
}
