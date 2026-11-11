package com.renanpontes.isekaimahou.feature.class_steelball.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.classes.ClassResources;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFx;
import com.renanpontes.isekaimahou.feature.class_steelball.SbProgress;
import com.renanpontes.isekaimahou.feature.class_steelball.SbRegistry;
import com.renanpontes.isekaimahou.feature.class_steelball.SbSpin;
import com.renanpontes.isekaimahou.feature.class_steelball.item.SteelBallItem;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A bola de aço girando. Três origens:
 * <ul>
 *   <li>{@link Mode#ITEM} — arremessada pelo item Bola de Aço: dano físico com rotação, aplica Girado;</li>
 *   <li>{@link Mode#SPELL} — forma "Bola de Aço" ({@code spin_ball}): leva a magia; Perfurar, Ricochete e Teleguiado valem;</li>
 *   <li>{@link Mode#MOB} — o Mestre da Rotação no desafio.</li>
 * </ul>
 * Depois de acertar (ou de voar o tempo máximo) a bola VOLTA para quem a lançou, atravessando tudo, e é apanhada no ar.
 */
public class SteelBallEntity extends ThrowableProjectile implements GeoEntity {
    public enum Mode { ITEM, SPELL, MOB }

    public static final int ITEM_COOLDOWN = 60;
    private static final int OUT_LIFE = 34;
    private static final int RETURN_LIFE = 120;

    private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(SteelBallEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_GOLD = SynchedEntityData.defineId(SteelBallEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_RETURN = SynchedEntityData.defineId(SteelBallEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(SteelBallEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.steelball_ball.spin");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int tier;
    private float damage = 5F;
    private float charge = 1F;
    @Nullable
    private CompoundTag spellTag;
    @Nullable
    private SpellContext ctx;
    private int pierce;
    private int ricochet;
    private boolean homing;
    private int returnTicks;
    private final Set<UUID> hit = new HashSet<>();

    public SteelBallEntity(EntityType<? extends SteelBallEntity> type, Level level) {
        super(type, level);
    }

    // =====================================================================================
    //  Fábricas
    // =====================================================================================

    /** Arremesso do item: {@code charge} 0,45..1 (tempo girando na mão). */
    public static SteelBallEntity thrown(ServerLevel level, Player player, int tier, float charge) {
        SteelBallEntity e = new SteelBallEntity(SbRegistry.BALL.get(), level);
        e.setMode(Mode.ITEM);
        e.setOwner(player);
        e.tier = tier;
        e.charge = charge;
        e.damage = SteelBallItem.throwDamage(tier);
        e.entityData.set(DATA_GOLD, tier >= 3);
        e.entityData.set(DATA_COLOR, tier >= 3 ? SbFx.GOLD : SbFx.STEEL);
        e.setPos(player.getX(), player.getEyeY() - 0.15, player.getZ());
        e.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.2F + 0.6F * charge, 0.6F);
        e.addTag(ClassResources.FOCUS_SHOT_TAG);
        return e;
    }

    /** Forma "Bola de Aço": sai de {@code from} na direção {@code dir}. */
    public static SteelBallEntity spell(SpellContext ctx, Vec3 from, Vec3 dir, float speed, boolean golden, int tier) {
        SteelBallEntity e = new SteelBallEntity(SbRegistry.BALL.get(), ctx.level());
        e.setMode(Mode.SPELL);
        if (ctx.caster() != null) e.setOwner(ctx.caster());
        e.tier = tier;
        e.setPos(from.x, from.y, from.z);
        e.shoot(dir.x, dir.y, dir.z, speed, 0.3F);
        SpellStats s = ctx.formStats();
        e.ctx = ctx;
        e.spellTag = ctx.save();
        e.pierce = Math.max(0, s.pierce);
        e.ricochet = Math.max(0, s.ricochet);
        e.homing = s.homing;
        e.entityData.set(DATA_GOLD, golden);
        e.entityData.set(DATA_COLOR, golden ? SbFx.GOLD : ctx.color() & 0xFFFFFF);
        if (ctx.previousTarget() != null) e.hit.add(ctx.previousTarget());
        e.addTag(ClassResources.FOCUS_SHOT_TAG);
        return e;
    }

    /** Bola do Mestre (desafio): mira em {@code target}. */
    public static SteelBallEntity mob(ServerLevel level, LivingEntity thrower, LivingEntity target, float damage, boolean golden) {
        SteelBallEntity e = new SteelBallEntity(SbRegistry.BALL.get(), level);
        e.setMode(Mode.MOB);
        e.setOwner(thrower);
        e.damage = damage;
        e.entityData.set(DATA_GOLD, golden);
        e.entityData.set(DATA_COLOR, golden ? SbFx.GOLD : SbFx.STEEL);
        Vec3 from = thrower.getEyePosition().add(thrower.getLookAngle().scale(0.4));
        e.setPos(from.x, from.y - 0.2, from.z);
        Vec3 aim = target.getEyePosition().subtract(0, 0.4, 0).subtract(from);
        e.shoot(aim.x, aim.y + aim.horizontalDistance() * 0.04, aim.z, 1.45F, 2.0F);
        return e;
    }

    // =====================================================================================
    //  Dados
    // =====================================================================================

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_MODE, (byte) 0);
        builder.define(DATA_GOLD, false);
        builder.define(DATA_RETURN, false);
        builder.define(DATA_COLOR, SbFx.STEEL);
    }

    public Mode mode() {
        int i = entityData.get(DATA_MODE);
        return Mode.values()[Math.max(0, Math.min(Mode.values().length - 1, i))];
    }

    private void setMode(Mode m) {
        entityData.set(DATA_MODE, (byte) m.ordinal());
    }

    public boolean golden() {
        return entityData.get(DATA_GOLD);
    }

    public boolean returning() {
        return entityData.get(DATA_RETURN);
    }

    public int color() {
        return entityData.get(DATA_COLOR);
    }

    @Override
    protected double getDefaultGravity() {
        return mode() == Mode.SPELL ? 0.01 : 0.025;
    }

    @Nullable
    private SpellContext context() {
        if (ctx == null && spellTag != null && level() instanceof ServerLevel sl) ctx = SpellContext.load(sl, spellTag);
        return ctx;
    }

    @Nullable
    private LivingEntity ownerLiving() {
        return getOwner() instanceof LivingEntity le ? le : null;
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================

    @Override
    public void tick() {
        if (returning()) {
            tickReturn();
            return;
        }
        super.tick();
        if (!(level() instanceof ServerLevel sl)) return;
        if (tickCount > OUT_LIFE) {
            if (mode() == Mode.SPELL) {
                SpellContext c = context();
                if (c != null) c.end(position(), getDeltaMovement());
            }
            startReturn();
            return;
        }
        if (homing && tickCount > 3) steer();
        if (tickCount % 2 == 0) {
            sl.sendParticles(SbFx.dust(color(), 0.8F), getX(), getY(), getZ(), 1, 0.02, 0.02, 0.02, 0);
        }
        if (golden() && tickCount % 6 == 0) SbFx.goldenSpiral(sl, position(), getDeltaMovement(), 0.6);
        if (tickCount % 5 == 0) SbFx.sound(sl, position(), SoundEvents.CHAIN_STEP, 0.25F, 1.8F);
    }

    /** Volta para a mão: atravessa tudo e acelera até ser apanhada. */
    private void tickReturn() {
        baseTick();
        returnTicks++;
        LivingEntity owner = ownerLiving();
        if (owner == null || !owner.isAlive() || owner.level() != level() || returnTicks > RETURN_LIFE
                || owner.distanceToSqr(this) > 96 * 96) {
            if (!level().isClientSide) discard();
            return;
        }
        Vec3 target = owner.getEyePosition().subtract(0, 0.35, 0);
        Vec3 to = target.subtract(position());
        double dist = to.length();
        double speed = Math.min(2.4, 0.6 + returnTicks * 0.08);
        Vec3 v = dist < 1.0E-3 ? Vec3.ZERO : to.scale(Math.min(1.0, speed / dist));
        setDeltaMovement(v);
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        if (level() instanceof ServerLevel sl) {
            if (tickCount % 2 == 0) sl.sendParticles(SbFx.dust(color(), 0.6F), getX(), getY(), getZ(), 1, 0, 0, 0, 0);
            if (dist < 1.4) caught(sl, owner);
        }
    }

    private void startReturn() {
        if (returning()) return;
        entityData.set(DATA_RETURN, true);
        noPhysics = true;
        setNoGravity(true);
        returnTicks = 0;
    }

    /** Apanhada no ar: libera o item, devolve um pouco de Foco ao Steel Ball. */
    private void caught(ServerLevel sl, LivingEntity owner) {
        if (owner instanceof ServerPlayer sp) {
            if (mode() == Mode.ITEM) {
                for (ItemStack st : sp.getInventory().items) {
                    if (st.getItem() instanceof SteelBallItem) sp.getCooldowns().removeCooldown(st.getItem());
                }
                ItemStack off = sp.getOffhandItem();
                if (off.getItem() instanceof SteelBallItem) sp.getCooldowns().removeCooldown(off.getItem());
            }
            if (MagicApi.get(sp).path() == MagicPath.STEELBALL) {
                ClassResources.gain(sp, MagicApi.get(sp).maxMana() * 0.01F);
            }
        }
        SbFx.sound(sl, owner.position(), SoundEvents.ARMOR_EQUIP_IRON.value(), 0.6F, 1.6F);
        discard();
    }

    private void steer() {
        SpellContext c = context();
        if (c == null) return;
        LivingEntity best = null;
        double bestD = 14 * 14;
        for (LivingEntity e : c.enemiesAround(position(), 14)) {
            if (hit.contains(e.getUUID())) continue;
            double d = e.distanceToSqr(this);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        if (best == null) return;
        Vec3 v = getDeltaMovement();
        double speed = v.length();
        if (speed < 1.0E-3) return;
        Vec3 to = best.position().add(0, best.getBbHeight() / 2, 0).subtract(position()).normalize();
        setDeltaMovement(v.normalize().lerp(to, 0.3).normalize().scale(speed));
        hasImpulse = true;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (returning() || !super.canHitEntity(target) || hit.contains(target.getUUID())) return false;
        LivingEntity owner = ownerLiving();
        if (owner == null) return true;
        if (target == owner) return false;
        if (mode() == Mode.SPELL) {
            SpellContext c = context();
            return c == null || !c.isAlly(target);
        }
        if (target instanceof OwnableEntity own && owner.getUUID().equals(own.getOwnerUUID())) return false;
        Boolean social = com.renanpontes.isekaimahou.core.api.Party.allyOverride(owner, target);
        return social == null || !social;
    }

    // =====================================================================================
    //  Acertos
    // =====================================================================================

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(level() instanceof ServerLevel sl) || returning()) return;
        Entity target = result.getEntity();
        LivingEntity owner = ownerLiving();
        Vec3 at = result.getLocation();
        hit.add(target.getUUID());
        switch (mode()) {
            case ITEM -> {
                float amount = damage * charge * SbProgress.spinMultiplier(owner, false);
                target.invulnerableTime = 0;
                if (target.hurt(damageSources().thrown(this, owner), amount) && target instanceof LivingEntity le) {
                    SbSpin.apply(le, owner, charge >= 0.95F ? 1 : 0, 60);
                    Vec3 k = getDeltaMovement().normalize();
                    le.knockback(0.5 * charge, -k.x, -k.z);
                    if (owner instanceof ServerPlayer sp) MagicApi.addCounter(sp, SbProgress.BALL_HITS, 1);
                }
            }
            case SPELL -> {
                SpellContext c = context();
                target.invulnerableTime = 0;
                target.hurt(damageSources().thrown(this, owner), 2.0F + tier);
                if (owner instanceof ServerPlayer sp && target instanceof LivingEntity) MagicApi.addCounter(sp, SbProgress.BALL_HITS, 1);
                if (c != null) {
                    c.impact(at, getDeltaMovement(), target);
                    SpellScheduler.schedule(1, () -> {
                        if (target.isAlive() && target.level() == sl) {
                            target.invulnerableTime = 0;
                            c.resolveOnEntity(target, this);
                        }
                    });
                }
            }
            case MOB -> {
                target.invulnerableTime = 0;
                if (target.hurt(damageSources().mobProjectile(this, owner), damage) && target instanceof LivingEntity le) {
                    SbSpin.apply(le, owner, golden() ? 1 : 0, 50);
                }
            }
        }
        SbFx.impact(sl, at, golden());
        SbFx.sound(sl, at, SoundEvents.ANVIL_LAND, 0.35F, 1.9F);
        if (mode() == Mode.SPELL && pierce > 0) {
            pierce--;
            return;
        }
        startReturn();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!(level() instanceof ServerLevel sl) || returning()) return;
        Vec3 at = result.getLocation();
        if (mode() == Mode.SPELL) {
            if (ricochet > 0) {
                ricochet--;
                Vec3 n = Vec3.atLowerCornerOf(result.getDirection().getNormal());
                Vec3 v = getDeltaMovement();
                setDeltaMovement(v.subtract(n.scale(2 * v.dot(n))).scale(0.9));
                hasImpulse = true;
                SbFx.sound(sl, at, SoundEvents.CHAIN_HIT, 0.7F, 1.6F);
                return;
            }
            SpellContext c = context();
            if (c != null) {
                c.impact(at, getDeltaMovement(), null);
                c.resolveOnBlock(result, this);
            }
        }
        SbFx.impact(sl, at, golden());
        SbFx.sound(sl, at, SoundEvents.CHAIN_PLACE, 0.6F, 1.5F);
        startReturn();
    }

    @Override
    protected void onHit(HitResult result) {
        if (returning()) return;
        super.onHit(result);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "spin", 0, state -> state.setAndContinue(SPIN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // =====================================================================================
    //  Salvar
    // =====================================================================================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Mode", (byte) mode().ordinal());
        tag.putBoolean("Gold", golden());
        tag.putBoolean("Return", returning());
        tag.putInt("Color", color());
        tag.putInt("Tier", tier);
        tag.putFloat("Damage", damage);
        tag.putFloat("Charge", charge);
        tag.putInt("Pierce", pierce);
        tag.putInt("Ricochet", ricochet);
        tag.putBoolean("Homing", homing);
        if (ctx != null) tag.put("Spell", ctx.save());
        else if (spellTag != null) tag.put("Spell", spellTag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setMode(Mode.values()[Math.max(0, Math.min(Mode.values().length - 1, tag.getByte("Mode")))]);
        entityData.set(DATA_GOLD, tag.getBoolean("Gold"));
        entityData.set(DATA_RETURN, tag.getBoolean("Return"));
        entityData.set(DATA_COLOR, tag.getInt("Color"));
        tier = tag.getInt("Tier");
        damage = tag.contains("Damage") ? tag.getFloat("Damage") : 5F;
        charge = tag.contains("Charge") ? tag.getFloat("Charge") : 1F;
        pierce = tag.getInt("Pierce");
        ricochet = tag.getInt("Ricochet");
        homing = tag.getBoolean("Homing");
        spellTag = tag.contains("Spell", Tag.TAG_COMPOUND) ? tag.getCompound("Spell") : null;
        if (returning()) {
            noPhysics = true;
            setNoGravity(true);
        }
    }
}
