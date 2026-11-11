package com.renanpontes.isekaimahou.feature.class_gunslinger.entity;

import java.util.EnumSet;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.class_gunslinger.GsFx;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GunKind;
import com.renanpontes.isekaimahou.feature.class_gunslinger.duel.DuelService;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Forasteiro Espectral: o pistoleiro fantasma do Duelo ao Meio-Dia (GeckoLib: chapéu, poncho, revólver na mão).
 * <ul>
 *   <li><b>Duelo</b> ({@link DuelService}): aparece a 14 blocos, fica parado e imune durante a contagem ("3... 2... 1...
 *       SAQUE!"), saca com um tempo de reação (grau I 14 ticks, grau II "Ceifador do Meio-Dia" 8) e luta atirando,
 *       andando de lado, recarregando a cada 6 tiros (a brecha!) e — no grau II — esquivando e esvaziando o tambor em
 *       leque. Se o desafiante sair, morrer ou demorar 2 min, ele tira o chapéu e some.</li>
 *   <li><b>Livre</b> (ovo): um pistoleiro hostil que atira em jogadores.</li>
 * </ul>
 * Vida/dano do mod já são inflados pelo núcleo (MobScaling). Não é salvo no meio do duelo (some ao recarregar).
 */
public class OutlawEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(OutlawEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(OutlawEntity.class, EntityDataSerializers.BYTE);

    public static final byte FREE = 0;
    public static final byte COUNTDOWN = 1;
    public static final byte FIGHT = 2;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.gunslinger_outlaw.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.gunslinger_outlaw.walk");
    private static final RawAnimation READY = RawAnimation.begin().thenLoop("animation.gunslinger_outlaw.ready");
    private static final RawAnimation AIM = RawAnimation.begin().thenLoop("animation.gunslinger_outlaw.aim");
    private static final RawAnimation AIM_WALK = RawAnimation.begin().thenLoop("animation.gunslinger_outlaw.aim_walk");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Nullable private UUID duelist;
    private long drawTick;
    private long startTick;
    private int rounds = 6;
    private long reloadUntil;
    private long nextShot;
    private int fanLeft;
    private boolean vanishOnLoad;
    private boolean perfectDone;

    public OutlawEntity(EntityType<? extends OutlawEntity> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TIER, 1);
        builder.define(PHASE, FREE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new CombatGoal());
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, e -> phase() == FREE));
    }

    // =====================================================================================
    //  Duelo
    // =====================================================================================
    public int tier() { return entityData.get(TIER); }
    public byte phase() { return entityData.get(PHASE); }
    @Nullable public UUID duelist() { return duelist; }
    public long drawTick() { return drawTick; }

    /** Grau II: Ceifador do Meio-Dia (mais vida, mais rápido, esquiva e leque). */
    public void setTier(int tier) {
        entityData.set(TIER, Mth.clamp(tier, 1, 2));
        var hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(tier >= 2 ? 200.0 : 80.0);
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(tier >= 2 ? 0.34 : 0.3);
        setHealth(getMaxHealth());
    }

    /** Começa o duelo: imune e parado até {@code drawTick}. */
    public void startDuel(ServerPlayer player, long now, long draw) {
        duelist = player.getUUID();
        startTick = now;
        drawTick = draw;
        entityData.set(PHASE, COUNTDOWN);
        setPersistenceRequired();
        setTarget(null);
    }

    @Nullable
    private ServerPlayer duelPlayer() {
        if (duelist == null || !(level() instanceof ServerLevel sl)) return null;
        return sl.getServer().getPlayerList().getPlayer(duelist);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (vanishOnLoad) {
            discard();
            return;
        }
        if (duelist == null) return;
        ServerLevel level = (ServerLevel) level();
        long now = level.getGameTime();
        ServerPlayer p = duelPlayer();
        boolean gone = p == null || !p.isAlive() || p.level() != level || p.distanceToSqr(this) > 48 * 48;
        if (gone || (phase() == FIGHT && now - drawTick > DuelService.TIME_LIMIT)) {
            DuelService.lose(p, this, gone ? "gone" : "timeout");
            vanish();
            return;
        }
        if (phase() == COUNTDOWN) {
            getNavigation().stop();
            getLookControl().setLookAt(p, 30, 30);
            setYRot(Mth.rotLerp(0.3F, getYRot(), (float) (Mth.atan2(p.getZ() - getZ(), p.getX() - getX()) * Mth.RAD_TO_DEG) - 90F));
            yBodyRot = getYRot();
            DuelService.countdown(p, this, now);
            if (now >= drawTick) {
                entityData.set(PHASE, FIGHT);
                setTarget(p);
                int reaction = tier() >= 2 ? 8 : 14;
                nextShot = now + reaction;
                triggerAnim("action", "draw");
                DuelService.draw(p, this);
            }
        }
    }

    /** Some com fumaça e um aceno de chapéu. */
    public void vanish() {
        if (level() instanceof ServerLevel level) {
            triggerAnim("action", "tip_hat");
            level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 30, 0.4, 0.8, 0.4, 0.02);
            level.sendParticles(GsFx.dust(0xFFD86B, 1.2F), getX(), getY() + 1, getZ(), 20, 0.4, 0.8, 0.4, 0);
            GsFx.sound(level, position(), SoundEvents.WIND_CHARGE_BURST.value(), 0.8F, 0.8F);
        }
        duelist = null;
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (phase() == COUNTDOWN) {
            // atirou antes do "SAQUE!": queimou a largada
            if (source.getEntity() instanceof ServerPlayer sp && sp.getUUID().equals(duelist)) DuelService.foul(sp, this);
            return false;
        }
        // Saque perfeito: o desafiante acertou logo depois do sinal, antes do forasteiro reagir
        if (phase() == FIGHT && source.getEntity() instanceof ServerPlayer sp && sp.getUUID().equals(duelist)
                && level().getGameTime() - drawTick <= DuelService.PERFECT_DRAW && !perfectDone) {
            perfectDone = true;
            amount *= 2.0F;
            DuelService.perfectDraw(sp, this);
        }
        boolean ok = super.hurt(source, amount);
        if (ok && tier() >= 2 && phase() == FIGHT && random.nextFloat() < 0.25F) dodge();
        return ok;
    }

    /** Esquiva (grau II): um passo rápido para o lado, se houver espaço. */
    private void dodge() {
        if (!(level() instanceof ServerLevel level)) return;
        LivingEntity t = getTarget();
        Vec3 fwd = t == null ? Vec3.directionFromRotation(0, getYRot()) : t.position().subtract(position()).multiply(1, 0, 1).normalize();
        Vec3 side = new Vec3(-fwd.z, 0, fwd.x).scale(random.nextBoolean() ? 2.5 : -2.5);
        AABB moved = getBoundingBox().move(side);
        if (!level.noCollision(this, moved) || level.noCollision(this, moved.move(0, -1, 0))) return;
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.8, getZ(), 10, 0.3, 0.6, 0.3, 0.02);
        setPos(getX() + side.x, getY(), getZ() + side.z);
        GsFx.sound(level, position(), SoundEvents.PHANTOM_FLAP, 0.8F, 1.6F);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && duelist != null) {
            ServerPlayer p = duelPlayer();
            if (p != null) DuelService.win(p, this);
            duelist = null;
        }
        super.die(source);
    }

    // =====================================================================================
    //  Tiro
    // =====================================================================================
    private void shoot(LivingEntity target) {
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 eye = getEyePosition();
        double dist = distanceTo(target);
        // mira com antecipação (velocidade do alvo × tempo de voo)
        Vec3 lead = target.getDeltaMovement().multiply(1, 0, 1).scale(dist / 3.6);
        Vec3 aim = target.getEyePosition().add(0, -0.25, 0).add(lead).subtract(eye).normalize();
        Vec3 muzzle = GsFx.muzzle(this, aim, 1);
        float dmg = tier() >= 2 ? 8.0F : 5.0F;
        float spread = tier() >= 2 ? 1.2F : 2.5F;
        int rgb = tier() >= 2 ? 0xFF4A3A : 0xBFE8FF;
        ManaBulletEntity b = ManaBulletEntity.plain(level, this, muzzle, dmg, rgb, ManaBulletEntity.STYLE_NORMAL).fire(aim, 3.6F, spread);
        level.addFreshEntity(b);
        GsFx.shot(level, muzzle, aim, rgb, GunKind.REVOLVER);
        triggerAnim("action", "shoot");
        rounds--;
    }

    /** IA de combate: anda de lado na distância boa, atira com cadência, recarrega a cada 6 tiros. */
    private final class CombatGoal extends Goal {
        private int strafeTicks;
        private boolean strafeRight;

        CombatGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive() && phase() != COUNTDOWN;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            setAggressive(true); // sincronizado: o cliente mostra a pose de mira
        }

        @Override
        public void stop() {
            getMoveControl().strafe(0, 0);
            setAggressive(false);
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) return;
            long now = level().getGameTime();
            getLookControl().setLookAt(t, 30, 30);
            double d = distanceTo(t);
            boolean see = hasLineOfSight(t);
            if (--strafeTicks <= 0) {
                strafeTicks = 20 + random.nextInt(25);
                strafeRight = random.nextBoolean();
            }
            float fwd = d > 16 ? 0.6F : d < 7 ? -0.5F : 0F;
            if (!see) {
                getNavigation().moveTo(t, 1.0);
            } else {
                getNavigation().stop();
                getMoveControl().strafe(fwd, strafeRight ? 0.45F : -0.45F);
            }
            if (now < reloadUntil) return;
            if (fanLeft > 0 && rounds > 0) {
                if (now % 2 == 0) {
                    shoot(t);
                    fanLeft--;
                }
                return;
            }
            if (!see || now < nextShot) return;
            if (rounds <= 0) {
                rounds = 6;
                reloadUntil = now + (tier() >= 2 ? 30 : 44);
                triggerAnim("action", "reload");
                if (level() instanceof ServerLevel sl) GsFx.reloadStart(sl, position().add(0, 1.2, 0), GunKind.REVOLVER);
                return;
            }
            if (tier() >= 2 && rounds >= 4 && random.nextFloat() < 0.18F) {
                fanLeft = rounds;
                triggerAnim("action", "fan");
                return;
            }
            shoot(t);
            nextShot = now + (tier() >= 2 ? 14 : 22) + random.nextInt(6);
        }
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 4, state -> {
            if (phase() == COUNTDOWN) return state.setAndContinue(READY);
            boolean aiming = phase() == FIGHT || isAggressive();
            if (aiming) return state.setAndContinue(state.isMoving() ? AIM_WALK : AIM);
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "action", 1, state -> PlayState.STOP)
                .triggerableAnim("draw", RawAnimation.begin().thenPlay("animation.gunslinger_outlaw.draw"))
                .triggerableAnim("shoot", RawAnimation.begin().thenPlay("animation.gunslinger_outlaw.shoot"))
                .triggerableAnim("reload", RawAnimation.begin().thenPlay("animation.gunslinger_outlaw.reload"))
                .triggerableAnim("fan", RawAnimation.begin().thenPlay("animation.gunslinger_outlaw.fan"))
                .triggerableAnim("tip_hat", RawAnimation.begin().thenPlay("animation.gunslinger_outlaw.tip_hat")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // =====================================================================================
    //  Sons e salvamento
    // =====================================================================================
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PILLAGER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return tier() >= 2 ? 0.7F : 0.85F;
    }

    /** No Pacífico o forasteiro livre some, mas o do duelo fica até o fim (o duelo é um desafio pedido). */
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return duelist == null;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return duelist == null && super.removeWhenFarAway(distSqr);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Tier", tier());
        tag.putBoolean("Dueling", duelist != null);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Tier")) entityData.set(TIER, Mth.clamp(tag.getInt("Tier"), 1, 2));
        // um duelo não sobrevive a salvar/carregar: o forasteiro some no primeiro tick
        vanishOnLoad = tag.getBoolean("Dueling");
    }
}
