package com.renanpontes.isekaimahou.feature.legends_f.entity;

import java.util.List;

import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.legends_f.LfRegistry;
import com.renanpontes.isekaimahou.feature.legends_f.LfUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Mãos da Inveja (homenagem leve à Bruxa da Inveja de Re:Zero): mãos negras de sombra brotam do chão em volta do
 * ponto, agarram os inimigos por perto (uma mão por inimigo), arrastam todos para o centro e os apertam — dano sombrio
 * a cada meio segundo, Lentidão forte e Corrupção. Depois de alguns segundos as mãos afundam de volta.
 */
public class EnvyHandsEntity extends LfSpellEntity {
    public static final float DAMAGE = 3.5F;
    public static final int RISE = 10;
    public static final int SINK = 12;
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(EnvyHandsEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_HANDS = SynchedEntityData.defineId(EnvyHandsEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> DATA_GRIP = SynchedEntityData.defineId(EnvyHandsEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private int[] grip = new int[0];

    public EnvyHandsEntity(EntityType<? extends EnvyHandsEntity> type, Level level) {
        super(type, level);
    }

    public static void spawn(SpellContext ctx, Vec3 at, float radius, int hands, int life, SpellStats stats) {
        EnvyHandsEntity e = LfRegistry.ENVY_HANDS.get().create(ctx.level());
        if (e == null) return;
        e.bind(ctx, stats, 0x2A0F3A, life);
        e.entityData.set(DATA_RADIUS, radius);
        e.entityData.set(DATA_HANDS, Mth.clamp(hands, 3, 12));
        e.moveTo(at.x, at.y, at.z, 0, 0);
        ctx.level().addFreshEntity(e);
        LfUtil.sound(ctx.level(), at, SoundEvents.WARDEN_DIG, 1.4F, 0.8F);
        LfUtil.sound(ctx.level(), at, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.6F, 0.5F);
    }

    @Override
    protected void defineExtraData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 6F);
        builder.define(DATA_HANDS, 6);
        builder.define(DATA_GRIP, new CompoundTag());
    }

    public float radius() { return entityData.get(DATA_RADIUS); }

    public int hands() { return entityData.get(DATA_HANDS); }

    /** Id da entidade agarrada por cada mão (ou -1), visto pelo cliente. */
    public int[] grips() {
        return entityData.get(DATA_GRIP).getIntArray("g");
    }

    /** Onde a mão {@code i} nasce do chão (fixo por entidade). */
    public Vec3 anchor(int i) {
        int n = hands();
        double a = Math.PI * 2 * i / n + (getId() % 7) * 0.3;
        double r = radius() * (0.55 + 0.25 * ((i * 37 + getId()) % 5) / 4.0);
        return position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
    }

    @Override
    protected void serverTick(ServerLevel level, SpellContext ctx) {
        int life = life();
        if (age >= life + SINK) {
            discard();
            return;
        }
        if (age < RISE || age >= life) {
            if (age == life) {
                grip = new int[0];
                entityData.set(DATA_GRIP, new CompoundTag());
                LfUtil.sound(level, position(), SoundEvents.SCULK_BLOCK_BREAK, 1.2F, 0.5F);
            }
            return;
        }
        Vec3 c = position();
        if (age % 5 == 0 || grip.length == 0) {
            List<LivingEntity> foes = LfUtil.foesAround(ctx, c.add(0, 1, 0), radius() + 1.5, hands());
            int[] g = new int[hands()];
            for (int i = 0; i < g.length; i++) g[i] = i < foes.size() ? foes.get(i).getId() : -1;
            if (!java.util.Arrays.equals(g, grip)) {
                grip = g;
                CompoundTag t = new CompoundTag();
                t.putIntArray("g", g);
                entityData.set(DATA_GRIP, t);
            }
        }
        for (int id : grip) {
            if (id < 0) continue;
            Entity e = level.getEntity(id);
            if (!(e instanceof LivingEntity le) || !le.isAlive()) continue;
            Vec3 toC = c.subtract(le.position());
            double d = toC.horizontalDistance();
            if (d > 0.8) {
                Vec3 pull = new Vec3(toC.x, 0, toC.z).normalize().scale(Math.min(0.35, d * 0.08));
                boolean big = com.renanpontes.isekaimahou.core.api.Statuses.isBossLike(le);
                le.setDeltaMovement(le.getDeltaMovement().scale(0.4).add(pull.scale(big ? 0.25 : 1.0)).add(0, -0.02, 0));
                le.hurtMarked = true;
            }
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3, false, false), ctx.caster());
            if (age % 10 == 0 && stats != null) {
                ctx.damage(le, DAMAGE, stats, this);
                Statuses.apply(le, Statuses.Kind.CORRUPTED, 1, 100, ctx.caster(), LfUtil.power(ctx, stats));
                level.sendParticles(ParticleTypes.SQUID_INK, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
            }
        }
        if (age % 20 == 0) LfUtil.sound(level, c, SoundEvents.SCULK_CATALYST_BLOOM, 0.8F, 0.5F);
    }
}
