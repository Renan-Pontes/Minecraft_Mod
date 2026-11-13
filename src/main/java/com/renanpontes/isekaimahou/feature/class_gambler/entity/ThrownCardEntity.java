package com.renanpontes.isekaimahou.feature.class_gambler.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.classes.ClassResources;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFxNet;
import com.renanpontes.isekaimahou.feature.class_gambler.GbRegistries;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Carta carregada de energia (homenagem ao Gambit) — desenhada como a carta girando de chapa, com rastro na cor da magia:
 * <ul>
 *   <li>{@link Mode#SPELL}: forma Carta — leva a magia (Perfurar, Ricochete e Teleguiado valem) e estoura ao acertar;</li>
 *   <li>{@link Mode#PHYSICAL}: carta jogada à mão (Baralho Arcano ou Carta de Baralho): dano físico;</li>
 *   <li>{@link Mode#COIN}: ficha do Caça-Níquel — procura o inimigo mais próximo sozinha e leva a magia.</li>
 * </ul>
 * Vive pouco (3 s) e não é salva no mundo.
 */
public class ThrownCardEntity extends ThrowableItemProjectile {
    public enum Mode { SPELL, PHYSICAL, COIN }

    private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(ThrownCardEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(ThrownCardEntity.class, EntityDataSerializers.INT);
    private static final int LIFE = 60;
    private static final double HOMING_RANGE = 16;

    @Nullable
    private SpellContext ctx;
    private int pierce;
    private int ricochet;
    private boolean homing;
    private float physicalDamage = 3F;
    private final Set<UUID> hit = new HashSet<>();

    public ThrownCardEntity(EntityType<? extends ThrownCardEntity> type, Level level) {
        super(type, level);
    }

    // =====================================================================================
    //  Fábricas
    // =====================================================================================

    /** Carta da forma Carta (ou ficha do Caça-Níquel), saindo de {@code from} na direção {@code dir}. */
    public static ThrownCardEntity spell(SpellContext ctx, Vec3 from, Vec3 dir, float speed, Mode mode) {
        ThrownCardEntity e = new ThrownCardEntity(GbRegistries.THROWN_CARD.get(), ctx.level());
        e.setMode(mode);
        if (ctx.caster() != null) e.setOwner(ctx.caster());
        e.setPos(from.x, from.y, from.z);
        e.setItem(new ItemStack(mode == Mode.COIN ? GbRegistries.CASINO_CHIP.get() : GbRegistries.CARD.get()));
        e.shoot(dir.x, dir.y, dir.z, speed, 0.3F);
        SpellStats s = ctx.formStats();
        e.ctx = ctx;
        e.pierce = Math.max(0, s.pierce);
        e.ricochet = Math.max(0, s.ricochet);
        e.homing = s.homing || mode == Mode.COIN;
        e.entityData.set(DATA_COLOR, ctx.color() & 0xFFFFFF);
        if (ctx.previousTarget() != null) e.hit.add(ctx.previousTarget());
        e.addTag(ClassResources.FOCUS_SHOT_TAG);
        return e;
    }

    /** Carta jogada à mão (dano físico; segue a inflação de dano do mod, {@link Progression#DAMAGE_SCALE}). */
    public static ThrownCardEntity physical(Level level, LivingEntity owner, float damage, int color) {
        ThrownCardEntity e = new ThrownCardEntity(GbRegistries.THROWN_CARD.get(), level);
        e.setMode(Mode.PHYSICAL);
        e.setOwner(owner);
        e.setPos(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
        e.setItem(new ItemStack(GbRegistries.CARD.get()));
        e.physicalDamage = damage * Progression.DAMAGE_SCALE;
        e.entityData.set(DATA_COLOR, color & 0xFFFFFF);
        e.addTag(ClassResources.FOCUS_SHOT_TAG);
        return e;
    }

    // =====================================================================================
    //  Dados
    // =====================================================================================

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MODE, (byte) 0);
        builder.define(DATA_COLOR, 0xFFD86B);
    }

    public Mode mode() {
        int i = entityData.get(DATA_MODE);
        return Mode.values()[Math.max(0, Math.min(Mode.values().length - 1, i))];
    }

    private void setMode(Mode m) {
        entityData.set(DATA_MODE, (byte) m.ordinal());
    }

    public int color() {
        return entityData.get(DATA_COLOR);
    }

    @Override
    protected Item getDefaultItem() {
        return GbRegistries.CARD.get();
    }

    @Override
    protected double getDefaultGravity() {
        // carta "planando": quase reta
        return mode() == Mode.PHYSICAL ? 0.01 : 0.004;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================

    @Override
    public void tick() {
        super.tick();
        GbFxNet.trail(this);
        if (!(level() instanceof ServerLevel sl)) return;
        if (tickCount > LIFE) {
            if (ctx != null && mode() != Mode.PHYSICAL) ctx.end(position(), getDeltaMovement());
            discard();
            return;
        }
        if (homing && tickCount > 2) steer();
        // rastro em fita desenhado no cliente (GbFxNet.trail)
    }

    /** Teleguiado (ou ficha): curva para o inimigo mais próximo ainda não atingido. */
    private void steer() {
        if (ctx == null) return;
        LivingEntity best = null;
        double bestD = HOMING_RANGE * HOMING_RANGE;
        for (LivingEntity e : ctx.enemiesAround(position(), HOMING_RANGE)) {
            if (hit.contains(e.getUUID())) continue;
            double d = e.distanceToSqr(this);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        if (best == null) return;
        Vec3 v = getDeltaMovement();
        double speed = Math.max(0.6, v.length());
        Vec3 to = best.position().add(0, best.getBbHeight() * 0.5, 0).subtract(position()).normalize();
        setDeltaMovement(v.normalize().lerp(to, mode() == Mode.COIN ? 0.35 : 0.25).normalize().scale(speed));
        hasImpulse = true;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target) || hit.contains(target.getUUID())) return false;
        if (ctx != null) return !ctx.isAlly(target);
        Entity owner = getOwner();
        return owner == null || (target != owner && !owner.isAlliedTo(target));
    }

    // =====================================================================================
    //  Acertos
    // =====================================================================================

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(level() instanceof ServerLevel sl)) return;
        Entity target = result.getEntity();
        hit.add(target.getUUID());
        Vec3 at = result.getLocation();
        LivingEntity owner = getOwner() instanceof LivingEntity le ? le : null;
        if (owner instanceof ServerPlayer sp) MagicApi.addCounter(sp, "gambler.card_hit", 1);
        if (mode() == Mode.PHYSICAL) {
            target.hurt(damageSources().thrown(this, owner), physicalDamage);
            GbFx.sound(sl, at, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 1.9F);
            sl.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.2);
            discard();
            return;
        }
        SpellContext c = ctx;
        if (c != null) {
            c.impact(at, getDeltaMovement(), target);
            // um tick depois: sem a invencibilidade do impacto da carta
            SpellScheduler.schedule(1, () -> {
                if (target.isAlive() && target.level() == sl) {
                    target.invulnerableTime = 0;
                    c.resolveOnEntity(target, this);
                }
            });
        }
        GbFx.cardBurst(sl, at, color());
        if (pierce > 0) {
            pierce--;
        } else {
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!(level() instanceof ServerLevel sl)) return;
        Vec3 at = result.getLocation();
        if (mode() != Mode.PHYSICAL && ricochet > 0) {
            ricochet--;
            Vec3 n = Vec3.atLowerCornerOf(result.getDirection().getNormal());
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.subtract(n.scale(2 * v.dot(n))).scale(0.9));
            hasImpulse = true;
            GbFx.sound(sl, at, SoundEvents.CHAIN_HIT, 0.6F, 1.8F);
            return;
        }
        if (ctx != null && mode() != Mode.PHYSICAL) {
            ctx.impact(at, getDeltaMovement(), null);
            ctx.resolveOnBlock(result, this);
            GbFx.cardBurst(sl, at, color());
        } else {
            sl.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 4, 0.1, 0.1, 0.1, 0.1);
            GbFx.sound(sl, at, SoundEvents.BOOK_PAGE_TURN, 0.6F, 1.6F);
        }
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Mode", (byte) mode().ordinal());
        tag.putInt("Color", color());
        tag.putFloat("Damage", physicalDamage);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setMode(Mode.values()[Math.max(0, Math.min(Mode.values().length - 1, tag.getByte("Mode")))]);
        entityData.set(DATA_COLOR, tag.getInt("Color"));
        physicalDamage = tag.contains("Damage") ? tag.getFloat("Damage") : 3F;
    }
}
