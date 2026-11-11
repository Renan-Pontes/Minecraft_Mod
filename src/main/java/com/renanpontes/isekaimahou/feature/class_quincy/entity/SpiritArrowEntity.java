package com.renanpontes.isekaimahou.feature.class_quincy.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.feature.class_quincy.Quincy;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyFx;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyRegistries;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Flecha Espiritual (Heilig Pfeil): energia azul sem gravidade, que nunca é recolhida. Dois modos:
 * <ul>
 *   <li><b>magia</b> — carrega um {@link SpellContext} (formas Arco Espiritual, Chuva de Luz, Absorção, Vollständig) e
 *   aplica os EFEITOS da magia ao acertar; respeita Perfurar, Ricochete, Teleguiado, Arco, Sensível e Expandir;</li>
 *   <li><b>simples</b> — dano fixo (Cruz Quincy, NPCs), já com a inflação {@code DAMAGE_SCALE}.</li>
 * </ul>
 * Por ser uma {@link AbstractArrow}, o acerto de um jogador de Foco devolve Foco (regra do núcleo). Vive no máximo
 * {@link #MAX_LIFE} ticks e some ao bater num bloco.
 */
public class SpiritArrowEntity extends AbstractArrow {
    public static final int MAX_LIFE = 100;
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(SpiritArrowEntity.class, EntityDataSerializers.FLOAT);

    @Nullable
    private CompoundTag spellTag;
    @Nullable
    private SpellContext ctx;
    private float plainDamage = 4.0F;
    private int life = MAX_LIFE;
    private int pierceLeft;
    private int ricochetLeft;
    private boolean homing;
    private boolean sensitive;
    private float impactRadius;
    private final Set<UUID> hitEntities = new HashSet<>();

    public SpiritArrowEntity(EntityType<? extends SpiritArrowEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        this.pickup = Pickup.DISALLOWED;
    }

    /** Flecha que carrega a magia (sai de {@code from}; depois chame {@code shoot} e {@code addFreshEntity}). */
    public static SpiritArrowEntity spell(SpellContext ctx, LivingEntity shooter, Vec3 from) {
        SpiritArrowEntity a = new SpiritArrowEntity(QuincyRegistries.SPIRIT_ARROW.get(), ctx.level());
        a.setOwner(shooter);
        a.pickup = Pickup.DISALLOWED;
        a.setPos(from.x, from.y, from.z);
        a.ctx = ctx;
        a.spellTag = ctx.save();
        if (ctx.previousTarget() != null) a.hitEntities.add(ctx.previousTarget());
        SpellStats s = ctx.formStats();
        a.pierceLeft = Math.max(0, s.pierce);
        a.ricochetLeft = Math.max(0, s.ricochet);
        a.homing = s.homing;
        a.sensitive = s.sensitive;
        a.impactRadius = s.aoe > 0 ? s.radius(1.5F) : 0F;
        a.setNoGravity(!s.arc);
        a.entityData.set(SIZE, 1.0F + 0.2F * Math.max(0, s.amplify + s.charge));
        return a;
    }

    /** Flecha simples de dano fixo (antes da inflação). */
    public static SpiritArrowEntity plain(Level level, LivingEntity shooter, float damage) {
        SpiritArrowEntity a = new SpiritArrowEntity(QuincyRegistries.SPIRIT_ARROW.get(), level);
        a.setOwner(shooter);
        a.pickup = Pickup.DISALLOWED;
        a.setPos(shooter.getX(), shooter.getEyeY() - 0.12, shooter.getZ());
        a.plainDamage = damage;
        a.entityData.set(SIZE, Math.min(1.8F, 0.85F + damage / 20F));
        return a;
    }

    public SpiritArrowEntity pierce(int n) {
        this.pierceLeft += Math.max(0, n);
        return this;
    }

    public SpiritArrowEntity homing(boolean h) {
        this.homing |= h;
        return this;
    }

    public SpiritArrowEntity life(int ticks) {
        this.life = Math.max(10, Math.min(MAX_LIFE * 2, ticks));
        return this;
    }

    public SpiritArrowEntity size(float s) {
        this.entityData.set(SIZE, s);
        return this;
    }

    public boolean isSpell() {
        return spellTag != null;
    }

    public float size() {
        return entityData.get(SIZE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SIZE, 1.0F);
    }

    @Nullable
    private SpellContext context() {
        if (ctx == null && spellTag != null && level() instanceof ServerLevel sl) ctx = SpellContext.load(sl, spellTag);
        return ctx;
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.ARROW);
    }

    @Override
    protected float getWaterInertia() {
        return 0.99F; // energia: a água quase não freia
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    @Override
    public boolean isCritArrow() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            Vec3 v = getDeltaMovement();
            for (int i = 0; i < 2; i++) {
                double t = i / 2.0;
                level().addParticle(QuincyFx.REISHI_SMALL, getX() - v.x * t, getY() - v.y * t, getZ() - v.z * t, 0, 0, 0);
            }
            if (tickCount % 3 == 0) level().addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0, 0, 0);
            return;
        }
        if (isRemoved()) return;
        if (inGround || --life <= 0) {
            SpellContext c = context();
            if (c != null) c.end(position(), getDeltaMovement());
            discard();
            return;
        }
        if (homing && tickCount > 2 && tickCount % 2 == 0) steer();
    }

    private void steer() {
        LivingEntity best = null;
        double bestD = 16 * 16;
        SpellContext c = context();
        Entity owner = getOwner();
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(16), LivingEntity::isAlive)) {
            if (e == owner || hitEntities.contains(e.getUUID())) continue;
            if (c != null ? c.isAlly(e) : isFriend(owner, e)) continue;
            if (c == null && !(e instanceof net.minecraft.world.entity.monster.Enemy)) continue;
            double d = e.distanceToSqr(this);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        if (best == null) return;
        Vec3 to = best.position().add(0, best.getBbHeight() / 2, 0).subtract(position()).normalize();
        Vec3 v = getDeltaMovement();
        double speed = v.length();
        setDeltaMovement(v.normalize().lerp(to, 0.25).normalize().scale(speed));
        hasImpulse = true;
    }

    /** Aliado do dono (para flechas simples): ele mesmo, mesmo time, grupo/facção aliada ou outro Quincy NPC. */
    private static boolean isFriend(@Nullable Entity owner, Entity target) {
        if (owner == null) return false;
        if (owner == target || owner.isAlliedTo(target)) return true;
        if (owner instanceof LivingEntity lo) {
            Boolean social = Party.allyOverride(lo, target);
            if (social != null) return social;
        }
        return owner instanceof QuincyNpcEntity && target instanceof QuincyNpcEntity;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target) || hitEntities.contains(target.getUUID())) return false;
        if (spellTag == null && isFriend(getOwner(), target)) return false;
        return true;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(level() instanceof ServerLevel sl)) return;
        Entity target = result.getEntity();
        hitEntities.add(target.getUUID());
        if (spellTag != null) {
            SpellContext c = context();
            if (c == null) {
                discard();
                return;
            }
            c.impact(result.getLocation(), getDeltaMovement(), target);
            if (impactRadius > 0) c.resolveArea(result.getLocation(), impactRadius, this, sensitive);
            else c.resolveOnEntity(target, this);
        } else {
            Entity owner = getOwner();
            DamageSource src = new DamageSource(sl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(CoreRegistries.SPELL_DAMAGE), this, owner != null ? owner : this);
            target.hurt(src, plainDamage * SpellCost.damageScale());
            if (owner instanceof Player p && target instanceof LivingEntity le) com.renanpontes.isekaimahou.feature.class_quincy.QuincyEvents.onPlainHit(p, le);
        }
        QuincyFx.burst(sl, result.getLocation(), 8 + (int) (impactRadius * 6), 0.2 + impactRadius * 0.3);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_HIT, getSoundSource(), 0.7F, 1.6F + random.nextFloat() * 0.3F);
        if (pierceLeft > 0) {
            pierceLeft--;
        } else {
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (!(level() instanceof ServerLevel sl)) return;
        if (ricochetLeft > 0) {
            ricochetLeft--;
            Vec3 v = getDeltaMovement();
            Vec3 n = Vec3.atLowerCornerOf(result.getDirection().getNormal());
            setDeltaMovement(v.subtract(n.scale(2 * v.dot(n))));
            hasImpulse = true;
            QuincyFx.burst(sl, result.getLocation(), 6, 0.15);
            return;
        }
        SpellContext c = context();
        if (c != null) {
            c.impact(result.getLocation(), getDeltaMovement(), null);
            if (impactRadius > 0) c.resolveArea(result.getLocation(), impactRadius, this, true);
            else c.resolveOnBlock(result, this);
        }
        QuincyFx.burst(sl, result.getLocation(), 8, 0.15);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, getSoundSource(), 0.5F, 1.8F);
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ctx != null) tag.put("Spell", ctx.save());
        else if (spellTag != null) tag.put("Spell", spellTag);
        tag.putFloat("PlainDamage", plainDamage);
        tag.putInt("Life", life);
        tag.putInt("PierceLeft", pierceLeft);
        tag.putInt("Ricochet", ricochetLeft);
        tag.putBoolean("Homing", homing);
        tag.putBoolean("Sensitive", sensitive);
        tag.putFloat("Radius", impactRadius);
        tag.putFloat("Size", size());
        ListTag hit = new ListTag();
        for (UUID id : hitEntities) hit.add(NbtUtils.createUUID(id));
        tag.put("Hit", hit);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        spellTag = tag.contains("Spell", Tag.TAG_COMPOUND) ? tag.getCompound("Spell") : null;
        plainDamage = tag.getFloat("PlainDamage");
        life = tag.contains("Life") ? tag.getInt("Life") : MAX_LIFE;
        pierceLeft = tag.getInt("PierceLeft");
        ricochetLeft = tag.getInt("Ricochet");
        homing = tag.getBoolean("Homing");
        sensitive = tag.getBoolean("Sensitive");
        impactRadius = tag.getFloat("Radius");
        if (tag.contains("Size")) entityData.set(SIZE, tag.getFloat("Size"));
        hitEntities.clear();
        for (Tag t : tag.getList("Hit", Tag.TAG_INT_ARRAY)) hitEntities.add(NbtUtils.loadUUID(t));
        pickup = Pickup.DISALLOWED;
    }
}
