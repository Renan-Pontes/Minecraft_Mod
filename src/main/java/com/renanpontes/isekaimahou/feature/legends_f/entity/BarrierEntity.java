package com.renanpontes.isekaimahou.feature.legends_f.entity;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.legends_f.LfNet;
import com.renanpontes.isekaimahou.feature.legends_f.LfRegistry;
import com.renanpontes.isekaimahou.feature.legends_f.LfUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Barreira Defensiva Geral (homenagem a Frieren): uma cúpula de hexágonos de mana em volta do alvo. Projéteis inimigos
 * que tentam entrar se desfazem na parede, criaturas hostis são empurradas para fora, e golpes/magias vindos de fora
 * em quem está dentro batem na barreira (que absorve o dano até quebrar). Cada impacto acende os hexágonos; quando a
 * resistência acaba ela se estilhaça.
 */
public class BarrierEntity extends LfSpellEntity {
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(BarrierEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HP = SynchedEntityData.defineId(BarrierEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_HIT = SynchedEntityData.defineId(BarrierEntity.class, EntityDataSerializers.INT);

    private static final List<BarrierEntity> ACTIVE = new ArrayList<>();

    private float maxHp = 100F;
    private float hp = 100F;

    public BarrierEntity(EntityType<? extends BarrierEntity> type, Level level) {
        super(type, level);
    }

    public static void spawn(SpellContext ctx, Vec3 at, float radius, float hp, int life, SpellStats stats) {
        BarrierEntity b = LfRegistry.BARRIER.get().create(ctx.level());
        if (b == null) return;
        b.bind(ctx, stats, 0x8FE6FF, life);
        b.entityData.set(DATA_RADIUS, radius);
        b.maxHp = hp;
        b.hp = hp;
        b.moveTo(at.x, at.y, at.z, 0, 0);
        ctx.level().addFreshEntity(b);
        LfUtil.sound(ctx.level(), at, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.6F);
        LfUtil.sound(ctx.level(), at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 1.2F);
    }

    @Override
    protected void defineExtraData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 3.5F);
        builder.define(DATA_HP, 1F);
        builder.define(DATA_HIT, -100);
    }

    public float radius() { return entityData.get(DATA_RADIUS); }

    /** Resistência restante 0..1 (o cliente racha os hexágonos conforme cai). */
    public float hpFraction() { return entityData.get(DATA_HP); }

    /** Idade do último impacto (o cliente acende a parede por alguns ticks). */
    public int lastHit() { return entityData.get(DATA_HIT); }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!level().isClientSide) ACTIVE.add(this);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        ACTIVE.remove(this);
    }

    private boolean inside(Vec3 p) {
        return p.distanceToSqr(position()) <= radius() * radius();
    }

    private boolean friendly(@Nullable Entity e) {
        if (e == null || ctx == null) return false;
        return e == ctx.caster() || (ctx.casterId() != null && e.getUUID().equals(ctx.casterId())) || ctx.isAlly(e);
    }

    @Override
    protected void serverTick(ServerLevel level, SpellContext ctx) {
        if (age >= life()) {
            fade(level);
            return;
        }
        float r = radius();
        Vec3 c = position();
        AABB box = new AABB(c, c).inflate(r + 2.5);
        // Projéteis inimigos que chegam na parede se desfazem
        for (Projectile p : level.getEntitiesOfClass(Projectile.class, box, p -> p.isAlive() && !friendly(p.getOwner()))) {
            Vec3 now = p.position();
            Vec3 next = now.add(p.getDeltaMovement());
            if (!inside(now) && (inside(next) || next.distanceToSqr(c) < (r + 0.6) * (r + 0.6))) {
                Vec3 at = c.add(now.subtract(c).normalize().scale(r));
                level.sendParticles(Vfx.electric(), at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.1);
                LfUtil.sound(level, at, SoundEvents.AMETHYST_BLOCK_HIT, 1.0F, 1.5F);
                p.discard();
                absorb(level, 6F);
                if (isRemoved()) return;
            }
        }
        // Criaturas hostis não entram: empurra para fora
        if (age % 2 == 0) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                    e -> e.isAlive() && !friendly(e) && e instanceof Mob m && (m.getTarget() != null || e instanceof net.minecraft.world.entity.monster.Enemy))) {
                Vec3 d = e.position().subtract(c);
                double dist = d.length();
                if (dist > r) continue;
                Vec3 out = dist < 0.01 ? new Vec3(1, 0, 0) : d.scale(1 / dist);
                e.setDeltaMovement(out.x * 0.6, 0.15, out.z * 0.6);
                e.hurtMarked = true;
                if (age % 10 == 0) entityData.set(DATA_HIT, age);
            }
        }
        if (age % 20 == 0) entityData.set(DATA_HP, Math.max(0F, hp / maxHp));
    }

    /** A barreira segurou {@code amount} de dano. */
    public void absorb(ServerLevel level, float amount) {
        hp -= amount;
        entityData.set(DATA_HIT, age);
        entityData.set(DATA_HP, Math.max(0F, hp / maxHp));
        if (hp <= 0) {
            LfUtil.fx(level, LfNet.SHATTER, position(), new Vec3(0, 1, 0), color(), 30, radius());
            LfUtil.sound(level, position(), SoundEvents.GLASS_BREAK, 1.4F, 0.7F);
            LfUtil.sound(level, position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1.4F, 0.6F);
            discard();
        }
    }

    private void fade(ServerLevel level) {
        level.sendParticles(Vfx.endRod(), getX(), getY() + radius() * 0.5, getZ(), 20, radius() * 0.5, radius() * 0.4, radius() * 0.5, 0.02);
        LfUtil.sound(level, position(), SoundEvents.BEACON_DEACTIVATE, 0.8F, 1.6F);
        discard();
    }

    /**
     * Um golpe/magia vindo de FORA (de {@code from}) vai acertar {@code victim}, que está DENTRO de uma barreira
     * amiga: a barreira absorve. Devolve true se o dano foi segurado.
     */
    public static boolean shield(ServerLevel level, LivingEntity victim, @Nullable Entity attacker, @Nullable Vec3 from, float amount) {
        if (ACTIVE.isEmpty() || from == null) return false;
        for (BarrierEntity b : new ArrayList<>(ACTIVE)) {
            if (b.isRemoved() || b.level() != level || b.ctx == null) continue;
            if (!b.inside(victim.position().add(0, victim.getBbHeight() * 0.4, 0)) || b.inside(from)) continue;
            if (!(victim == b.ctx.caster() || b.friendly(victim)) || b.friendly(attacker)) continue;
            Vec3 dir = from.subtract(b.position()).normalize();
            Vec3 at = b.position().add(dir.scale(b.radius()));
            level.sendParticles(Vfx.electric(), at.x, at.y, at.z, 12, 0.25, 0.25, 0.25, 0.12);
            LfUtil.sound(level, at, SoundEvents.AMETHYST_BLOCK_HIT, 1.2F, 1.3F);
            b.absorb(level, amount);
            return true;
        }
        return false;
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
