package com.renanpontes.isekaimahou.feature.forms_b.entity;

import java.util.UUID;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.forms_b.util.FormsBUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Invocação: espírito flutuante do conjurador. Segue o dono (por cima do ombro) e, a cada 40 ticks,
 * lança os efeitos da magia na ameaça mais próxima (16 blocos), com uma linha de partículas.
 * O movimento é calculado no cliente e no servidor (fica suave).
 */
public class SpellConjurerEntity extends SpellFormEntity implements GeoEntity, OwnableEntity {
    public static final int CAST_INTERVAL = 40;
    public static final double RANGE = 16.0;
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.spell_conjurer.idle");
    private static final RawAnimation CAST = RawAnimation.begin().thenPlay("animation.spell_conjurer.cast");

    private static final EntityDataAccessor<Integer> DATA_OWNER_NET = SynchedEntityData.defineId(SpellConjurerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SLOT = SynchedEntityData.defineId(SpellConjurerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(SpellConjurerEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SpellConjurerEntity(EntityType<? extends SpellConjurerEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineExtraData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER_NET, -1);
        builder.define(DATA_SLOT, 0);
        builder.define(DATA_TARGET, -1);
    }

    /** Liga ao dono; {@code slot} separa vários espíritos (Dividir) em volta dele. */
    public void bind(Entity owner, int slot) {
        entityData.set(DATA_OWNER_NET, owner.getId());
        entityData.set(DATA_SLOT, slot);
        Vec3 p = anchor(owner);
        setPos(p.x, p.y, p.z);
        setYRot(owner.getYRot());
        setOldPosAndRot();
    }

    @Override
    public UUID getOwnerUUID() {
        return ownerId();
    }

    private Entity ownerEntity() {
        int id = entityData.get(DATA_OWNER_NET);
        return id >= 0 ? level().getEntity(id) : null;
    }

    /** Ponto acima do ombro do dono, girando com ele; cada espírito extra fica num lado/altura. */
    private Vec3 anchor(Entity owner) {
        int slot = entityData.get(DATA_SLOT);
        float side = slot % 2 == 0 ? -1F : 1F;
        float yaw = (owner instanceof LivingEntity le ? le.yBodyRot : owner.getYRot()) * Mth.DEG_TO_RAD;
        double sideX = Math.cos(yaw), sideZ = Math.sin(yaw);
        double backX = Math.sin(yaw), backZ = -Math.cos(yaw);
        double dist = 1.1 + (slot / 2) * 0.7;
        double bob = Math.sin((tickCount + slot * 13) * 0.12) * 0.15;
        return new Vec3(owner.getX() + sideX * side * dist + backX * 0.6,
                owner.getY() + owner.getBbHeight() + 0.2 + (slot / 2) * 0.4 + bob,
                owner.getZ() + sideZ * side * dist + backZ * 0.6);
    }

    @Override
    public void tick() {
        Entity owner = ownerEntity();
        if (owner != null) {
            Vec3 goal = anchor(owner);
            Vec3 cur = position();
            Vec3 next = goal.distanceToSqr(cur) > 20 * 20 ? goal : cur.lerp(goal, 0.25);
            setPos(next.x, next.y, next.z);
            Entity t = entityData.get(DATA_TARGET) >= 0 ? level().getEntity(entityData.get(DATA_TARGET)) : null;
            Vec3 face = t != null ? t.position().subtract(position()) : owner.getLookAngle();
            if (face.horizontalDistanceSqr() > 1.0E-4) {
                float want = (float) (Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90.0F;
                setYRot(Mth.rotLerp(0.3F, getYRot(), want));
            }
        }
        super.tick();
    }

    /** Cliente: o espírito segue o dono por conta própria; ignorar a posição do servidor evita "tremidas". */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (level().isClientSide && ownerEntity() != null) return;
        super.lerpTo(x, y, z, yRot, xRot, steps);
    }

    @Override
    protected void serverTick(ServerLevel level, SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null || !caster.isAlive() || caster.level() != level) {
            fizzle();
            return;
        }
        if (entityData.get(DATA_OWNER_NET) != caster.getId()) entityData.set(DATA_OWNER_NET, caster.getId());
        if (age == 1) {
            ElementFx.burst(level, position(), element(), 20, 0.4);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.PLAYERS, 0.9F, 0.8F);
        }
        // Escolhe o alvo um pouco antes de lançar (o espírito vira para ele)
        if (age % CAST_INTERVAL == CAST_INTERVAL - 10) {
            LivingEntity t = FormsBUtil.nearestThreat(ctx, position(), RANGE, !ctx.formStats().ghost);
            entityData.set(DATA_TARGET, t != null ? t.getId() : -1);
        }
        if (age % CAST_INTERVAL != 0) return;
        Entity te = level.getEntity(entityData.get(DATA_TARGET));
        if (!(te instanceof LivingEntity target) || !FormsBUtil.isThreat(ctx, target)
                || target.distanceToSqr(this) > RANGE * RANGE) {
            entityData.set(DATA_TARGET, -1);
            return;
        }
        Vec3 from = position().add(0, 0.5, 0);
        Vec3 to = target.getBoundingBox().getCenter();
        ElementFx.line(level, from, to, elementFor(age / CAST_INTERVAL));
        level.sendParticles(FormsBUtil.dust(color(), 1.2F), to.x, to.y, to.z, 8, 0.2, 0.2, 0.2, 0);
        ctx.resolveOnEntity(target, this);
        triggerAnim("action", "cast");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.7F, 1.5F);
    }

    @Override
    protected void onExpire(ServerLevel level, SpellContext ctx) {
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.5, getZ(), 10, 0.2, 0.3, 0.2, 0.02);
        ElementFx.burst(level, position().add(0, 0.5, 0), element(), 14, 0.3);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ALLAY_DEATH, SoundSource.PLAYERS, 0.6F, 1.2F);
        // O espírito partiu: Ao Terminar nasce onde ele estava
        stageEnd(ctx, position().add(0, 0.5, 0));
    }

    @Override
    protected void clientTick() {
        if (FormsBUtil.reducedParticles() && age % 4 != 0) return;
        // Rastro etéreo saindo da cauda
        level().addParticle(FormsBUtil.dust(color(), 0.7F), xo + (random.nextDouble() - 0.5) * 0.2, yo + 0.1,
                zo + (random.nextDouble() - 0.5) * 0.2, 0, -0.01, 0);
        if (random.nextInt(6) == 0) {
            level().addParticle(ParticleTypes.ENCHANT, getX(), getY() + 0.9, getZ(),
                    (random.nextDouble() - 0.5) * 0.6, 0.3, (random.nextDouble() - 0.5) * 0.6);
        }
    }

    @Override
    protected void readExtra(CompoundTag tag) {
        entityData.set(DATA_SLOT, tag.getInt("Slot"));
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putInt("Slot", entityData.get(DATA_SLOT));
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> state.setAndContinue(IDLE)));
        // Controlador separado: o gesto de lançar sobrepõe os braços sem reiniciar a flutuação
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("cast", CAST));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
