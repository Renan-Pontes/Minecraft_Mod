package com.renanpontes.isekaimahou.feature.settlements.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.feature.settlements.Conquest;
import com.renanpontes.isekaimahou.feature.settlements.SettlementStyle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Campeão da Cidade: o desafio no coração de uma cidade de reino para virar o Senhor dela (docs/V10_DESIGN.md §3
 * "settlements"). Duelo de honra — só existe durante o desafio e some se o desafiante morrer, fugir ou o tempo
 * acabar. Arena selada pelo núcleo ({@code BossArena}), a cidade não é destruída.
 * <ul>
 *   <li>Espadão (combo), <b>Investida</b> (avança com o escudo), <b>Golpe Sísmico</b> (anel de choque);</li>
 *   <li>50%: <b>Brado de Honra</b> (Resistência + Velocidade, empurra quem está perto);</li>
 *   <li>25%: <b>Lâmina do Juramento</b> (giro contínuo que corta em volta).</li>
 * </ul>
 * Visual por reino (Aldória, Sahra, Nordheim) e vida pelo nível do desafiante.
 */
public class CityChampion extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<String> DATA_STYLE = SynchedEntityData.defineId(CityChampion.class, EntityDataSerializers.STRING);
    private static final ResourceLocation LEVEL_HP = IsekaiMahou.id("city_champion_level_hp");
    public static final int TIME_LIMIT = 6 * 60 * 20;
    public static final double LEASH = 48.0;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.isekai_mahou.city_champion"),
            BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
    @Nullable private UUID challenger;
    private String cityId = "";
    @Nullable private BlockPos heart;
    private int age;
    private int slamCooldown = 140;
    private int chargeCooldown = 100;
    private int spinCooldown = 60;
    private int slamAt = -1;
    private int chargeLeft;
    private int spinLeft;
    private boolean roared;
    private boolean finished;

    public CityChampion(EntityType<? extends CityChampion> type, Level level) {
        super(type, level);
        xpReward = 80;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 220.0)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.85);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STYLE, SettlementStyle.KINGDOM.id);
    }

    public SettlementStyle style() {
        SettlementStyle s = SettlementStyle.byId(entityData.get(DATA_STYLE));
        return s == null ? SettlementStyle.KINGDOM : s;
    }

    /** Prepara o desafio (antes de entrar no mundo). */
    public void setup(ServerPlayer who, String city, BlockPos heartPos, SettlementStyle style, int challengerLevel) {
        challenger = who.getUUID();
        cityId = city;
        heart = heartPos.immutable();
        entityData.set(DATA_STYLE, style.id);
        restrictTo(heart, 24);
        AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.removeModifier(LEVEL_HP);
            hp.addPermanentModifier(new AttributeModifier(LEVEL_HP, Math.max(0, challengerLevel) * 3.0, AttributeModifier.Operation.ADD_VALUE));
        }
        setHealth(getMaxHealth());
    }

    @Nullable
    public UUID challenger() {
        return challenger;
    }

    public String cityId() {
        return cityId;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        age++;
        if (age == 1) setHealth(getMaxHealth());
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (age % 20 == 0) {
            updateBossBar(sl);
            ServerPlayer who = challengerPlayer(sl);
            if (!finished && failed(sl, who)) {
                finished = true;
                Conquest.onChallengeFailed(sl, this, who);
                vanish(sl);
                return;
            }
            if (getTarget() == null && who != null && !who.isCreative()) setTarget(who);
        }
        if (age < 40) {
            getNavigation().stop();
            return;
        }
        LivingEntity target = getTarget();
        if (!roared && getHealth() < getMaxHealth() * 0.5F) roar(sl);
        if (slamAt > 0 && --slamAt == 0) slamImpact(sl);
        if (chargeLeft > 0) chargeTick(sl);
        if (spinLeft > 0) spinTick(sl);
        if (target == null || !target.isAlive()) return;
        double d = distanceToSqr(target);
        if (--slamCooldown <= 0 && d < 36 && slamAt < 0) {
            slamCooldown = 150 + random.nextInt(40);
            slamAt = 14;
            triggerAnim("action", "slam");
            playSound(SoundEvents.RAVAGER_ROAR, 1.2F, 1.3F);
        } else if (--chargeCooldown <= 0 && d > 36 && d < 256 && chargeLeft <= 0) {
            chargeCooldown = 110 + random.nextInt(40);
            chargeLeft = 16;
            triggerAnim("action", "charge");
            playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.4F, 0.7F);
            Vec3 dir = target.position().subtract(position()).multiply(1, 0, 1).normalize();
            setDeltaMovement(dir.scale(1.15).add(0, 0.15, 0));
        }
        if (getHealth() < getMaxHealth() * 0.25F && --spinCooldown <= 0 && spinLeft <= 0 && d < 49) {
            spinCooldown = 120;
            spinLeft = 30;
            triggerAnim("action", "spin");
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F, 0.6F);
        }
    }

    private boolean failed(ServerLevel sl, @Nullable ServerPlayer who) {
        if (age > TIME_LIMIT) return true;
        if (who == null || !who.isAlive() || who.level() != sl) return true;
        return heart != null && who.distanceToSqr(heart.getX() + 0.5, heart.getY(), heart.getZ() + 0.5) > LEASH * LEASH;
    }

    @Nullable
    private ServerPlayer challengerPlayer(ServerLevel sl) {
        return challenger == null ? null : sl.getServer().getPlayerList().getPlayer(challenger);
    }

    private void updateBossBar(ServerLevel sl) {
        for (ServerPlayer p : sl.players()) {
            boolean near = p.distanceToSqr(this) < 48 * 48;
            if (near && !bossBar.getPlayers().contains(p)) bossBar.addPlayer(p);
            else if (!near && bossBar.getPlayers().contains(p)) bossBar.removePlayer(p);
        }
    }

    private void slamImpact(ServerLevel sl) {
        sl.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.2, getZ(), 3, 1.0, 0.1, 1.0, 0.0);
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState());
        for (int i = 0; i < 36; i++) {
            double a = i * Math.PI * 2 / 36;
            sl.sendParticles(dust, getX() + Math.cos(a) * 4.5, getY() + 0.1, getZ() + Math.sin(a) * 4.5, 3, 0.2, 0.1, 0.2, 0.1);
        }
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 0.8F);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(5.0, 2.0, 5.0), this::foe)) {
            e.hurt(damageSources().mobAttack(this), 10.0F);
            Vec3 push = e.position().subtract(position()).multiply(1, 0, 1).normalize().scale(1.2);
            e.push(push.x, 0.55, push.z);
            e.hurtMarked = true;
        }
    }

    private void chargeTick(ServerLevel sl) {
        chargeLeft--;
        sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 2, 0.3, 0.1, 0.3, 0.01);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.8), this::foe)) {
            if (e.invulnerableTime > 0) continue;
            e.hurt(damageSources().mobAttack(this), 8.0F);
            Vec3 push = getDeltaMovement().normalize().scale(1.4);
            e.push(push.x, 0.4, push.z);
            e.hurtMarked = true;
            chargeLeft = 0;
        }
    }

    private void spinTick(ServerLevel sl) {
        spinLeft--;
        setYRot(getYRot() + 36.0F);
        setYBodyRot(getYRot());
        if (spinLeft % 2 == 0) {
            for (int i = 0; i < 8; i++) {
                double a = (spinLeft * 0.7 + i) * Math.PI / 4;
                sl.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + Math.cos(a) * 2.8, getY() + 1.2, getZ() + Math.sin(a) * 2.8,
                        1, 0, 0, 0, 0);
            }
        }
        if (spinLeft % 6 == 0) {
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.5, 1.0, 3.5), this::foe)) {
                e.hurt(damageSources().mobAttack(this), 6.0F);
            }
        }
    }

    private void roar(ServerLevel sl) {
        roared = true;
        triggerAnim("action", "roar");
        playSound(SoundEvents.RAID_HORN.value(), 2.0F, 1.0F);
        addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 240, 0));
        addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 240, 0));
        sl.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 1.5, getZ(), 1, 0, 0, 0, 0);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(6.0), this::foe)) {
            Vec3 push = e.position().subtract(position()).multiply(1, 0, 1).normalize().scale(1.6);
            e.push(push.x, 0.5, push.z);
            e.hurtMarked = true;
        }
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(this) < 40 * 40) {
                p.displayClientMessage(Component.translatable("settlements.isekai_mahou.champion.roar").withColor(style().color), true);
            }
        }
    }

    private boolean foe(LivingEntity e) {
        if (e == this || !e.isAlive()) return false;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        return e instanceof Mob m && m.getTarget() == this;
    }

    private void vanish(ServerLevel sl) {
        sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 30, 0.5, 1.0, 0.5, 0.05);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1.0F, 0.8F);
        bossBar.removeAllPlayers();
        discard();
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        triggerAnim("action", random.nextBoolean() ? "attack" : "attack2");
        return super.doHurtTarget(target);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (age < 40) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel sl && !finished) {
            finished = true;
            Conquest.onChampionDefeated(sl, this);
        }
        bossBar.removeAllPlayers();
        super.die(source);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    /** Ninguém do grupo do desafiante é ferido pelo golpe sísmico "por engano" — o duelo é de honra, mas o grupo ajuda. */
    public boolean partOfChallenge(Player p) {
        if (challenger == null) return false;
        if (p.getUUID().equals(challenger)) return true;
        Player who = level().getPlayerByUUID(challenger);
        return who != null && Party.sameParty(p, who);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (challenger != null) tag.putUUID("Challenger", challenger);
        tag.putString("City", cityId);
        if (heart != null) tag.put("Heart", NbtUtils.writeBlockPos(heart));
        tag.putString("Style", entityData.get(DATA_STYLE));
        tag.putInt("Age", age);
        tag.putBoolean("Roared", roared);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Challenger")) challenger = tag.getUUID("Challenger");
        cityId = tag.getString("City");
        heart = NbtUtils.readBlockPos(tag, "Heart").orElse(null);
        if (heart != null) restrictTo(heart, 24);
        entityData.set(DATA_STYLE, tag.getString("Style"));
        // Desafio salvo no meio (servidor reiniciou): o tempo continua contando de onde parou.
        age = Math.max(40, tag.getInt("Age"));
        roared = tag.getBoolean("Roared");
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================
    private static final String ANIM = "animation.city_champion.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(ANIM + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(ANIM + "walk");
    private static final RawAnimation INTRO = RawAnimation.begin().thenPlay(ANIM + "intro").thenLoop(ANIM + "idle");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<CityChampion>(this, "main", 5, state -> {
            if (tickCount < 40) return state.setAndContinue(INTRO);
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
        AnimationController<CityChampion> action = new AnimationController<>(this, "action", 2, state -> PlayState.STOP);
        for (String a : new String[] {"attack", "attack2", "slam", "charge", "spin", "roar"}) {
            action.triggerableAnim(a, RawAnimation.begin().thenPlay(ANIM + a));
        }
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
