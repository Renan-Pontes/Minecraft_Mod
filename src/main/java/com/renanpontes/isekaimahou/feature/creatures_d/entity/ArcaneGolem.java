package com.renanpontes.isekaimahou.feature.creatures_d.entity;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ai.AbilityGoal;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Golem Arcano: construto rúnico que guarda ruínas (fica a até 22 blocos do ponto onde surgiu).
 * Ignora jogadores até ser provocado — então as runas acendem e ele luta: soco pesado, pancada
 * arcana em anéis, rajada de setas arcanas (magia de glifo do núcleo) e, ferido, um escudo de runas.
 * Também destrói monstros que invadem a área que protege.
 */
public class ArcaneGolem extends ElementalCreature {
    /** Raio da área guardada (o golem não persegue nem mira alvos fora dela). */
    private static final int GUARD_RADIUS = 22;
    private static final EntityDataAccessor<Boolean> SHIELDED = SynchedEntityData.defineId(ArcaneGolem.class, EntityDataSerializers.BOOLEAN);
    private int shieldTicks;
    private BlockPos home;
    private boolean announced;

    public ArcaneGolem(EntityType<? extends ArcaneGolem> type, Level level) {
        super(type, level);
        this.xpReward = 30;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 90.0)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.5)
                .add(Attributes.MOVEMENT_SPEED, 0.23)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    public Element element() {
        return Element.ARCANE;
    }

    @Override
    protected String[] actionAnimations() {
        return new String[] {"attack", "slam", "cast", "shield"};
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SHIELDED, false);
    }

    public boolean isShielded() {
        return entityData.get(SHIELDED);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AbilityGoal(this, "shield", 0, 32, 10, 400, 500, true, t -> raiseShield())
                .when(t -> getHealth() < getMaxHealth() * 0.5F).ignoreSight());
        goalSelector.addGoal(2, new AbilityGoal(this, "slam", 0, 4.0, 20, 80, 120, true, t -> slam())
                .windup((t, left) -> {
                    if (level() instanceof ServerLevel sl && left % 4 == 0) {
                        MobMagic.ring(sl, position(), ElementFx.dust(Element.ARCANE, 1.2F), 4.0, 32, 0);
                    }
                }));
        goalSelector.addGoal(3, new AbilityGoal(this, "cast", 5, 20, 20, 70, 110, true, this::arcaneVolley)
                .windup((t, left) -> {
                    if (level() instanceof ServerLevel sl && left % 2 == 0) MobMagic.converge(sl, core(), ParticleTypes.ENCHANT, 2.0, 4);
                }));
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.8));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.5));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Mob.class, 5, false, false,
                e -> e instanceof Enemy && !(e instanceof Creeper) && !(e instanceof ElementalCreature) && insideGuard(e)));
    }

    /** O alvo está dentro da área guardada? (sem casa definida, ex.: /summon sem dados, vale qualquer lugar) */
    private boolean insideGuard(LivingEntity e) {
        return home == null || e.blockPosition().closerThan(home, GUARD_RADIUS);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, SpawnGroupData data) {
        setHome(blockPosition());
        // Guardiões colocados por estruturas ficam para sempre no lugar
        if (spawnType == MobSpawnType.STRUCTURE || spawnType == MobSpawnType.CHUNK_GENERATION) setPersistenceRequired();
        return super.finalizeSpawn(level, difficulty, spawnType, data);
    }

    private void setHome(BlockPos pos) {
        home = pos;
        restrictTo(pos, GUARD_RADIUS);
    }

    private Vec3 core() {
        return localPoint(0.45, 1.75, 0);
    }

    @Override
    public void setTarget(LivingEntity target) {
        super.setTarget(target);
        if (target == null) {
            announced = false;
        } else if (target instanceof Player && !announced && !level().isClientSide) {
            // Despertar do guardião
            announced = true;
            MobMagic.tellNear(this, 24, Component.translatable("message.isekai_mahou.arcane_golem.awaken").withStyle(ChatFormatting.LIGHT_PURPLE));
            playSoundHere(SoundEvents.BEACON_ACTIVATE, 1.2F, 0.6F);
            if (level() instanceof ServerLevel sl) ElementFx.burst(sl, core(), Element.ARCANE, 30, 0.6);
        }
    }

    /** Pancada arcana: anéis de energia se espalhando, dano, empurrão e lentidão. */
    private void slam() {
        if (!(level() instanceof ServerLevel sl)) return;
        Vec3 c = position();
        for (double r = 1.0; r <= 4.5; r += 0.9) {
            ElementFx.ring(sl, c, Element.ARCANE, r, (int) (r * 10));
        }
        sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.2, c.z, 2, 0.5, 0, 0.5, 0);
        for (LivingEntity e : MobMagic.victimsAround(this, c, 4.5)) {
            if (e.hurt(damageSources().mobAttack(this), 10.0F)) {
                MobMagic.knockAway(e, c, 1.0, 0.45);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), this);
            }
        }
        playSoundHere(SoundEvents.GENERIC_EXPLODE.value(), 0.7F, 0.6F);
        playSoundHere(SoundEvents.AMETHYST_BLOCK_BREAK, 1.2F, 0.6F);
    }

    /** Rajada arcana: duas setas em leque (magia do núcleo) ou três projéteis próprios. */
    private void arcaneVolley(LivingEntity target) {
        if (MobMagic.cast(this, target, Element.ARCANE, 1.2F, "projectile", "split", "arcane_damage")) return;
        MobMagic.aim(this, target);
        for (int i = 0; i < 3; i++) ElementalBolt.shoot(this, target, Element.ARCANE, 5.0F, 1.1F, 3.0F + i * 5.0F, Items.ENDER_EYE);
        playSoundHere(SoundEvents.EVOKER_CAST_SPELL, 1.0F, 0.8F);
    }

    private void raiseShield() {
        shieldTicks = 120;
        entityData.set(SHIELDED, true);
        addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 2));
        addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 0));
        if (level() instanceof ServerLevel sl) {
            for (int i = 0; i < 3; i++) ElementFx.ring(sl, position().add(0, 0.8 + i * 0.8, 0), Element.ARCANE, 1.6, 20);
        }
        playSoundHere(SoundEvents.BEACON_POWER_SELECT, 1.2F, 0.7F);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (shieldTicks > 0 && --shieldTicks == 0) entityData.set(SHIELDED, false);
        // Guardião não abandona as ruínas: quem foge para longe da área é deixado em paz e ele volta para casa
        LivingEntity t = getTarget();
        if (t != null && home != null && tickCount % 20 == 0 && !t.blockPosition().closerThan(home, GUARD_RADIUS + 8)) {
            // Para os objetivos de alvo (o TargetGoal reaplicaria o alvo lembrado) antes de limpar o alvo
            targetSelector.getAvailableGoals().stream().filter(WrappedGoal::isRunning).forEach(WrappedGoal::stop);
            setTarget(null);
            getNavigation().stop();
        }
    }

    @Override
    protected void onMeleeHit(LivingEntity target) {
        playSoundHere(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 0.6F);
        if (level() instanceof ServerLevel sl) ElementFx.burst(sl, target.position().add(0, target.getBbHeight() * 0.5, 0), Element.ARCANE, 10, 0.3);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) tag.putLong("Home", home.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Home")) setHome(BlockPos.of(tag.getLong("Home")));
    }

    @Override
    protected void clientEffects() {
        if (random.nextInt(3) == 0) {
            Vec3 c = core();
            level().addParticle(ParticleTypes.ENCHANT, c.x, c.y, c.z, random.nextGaussian() * 0.3, 0.2, random.nextGaussian() * 0.3);
        }
        if (isShielded()) {
            double a = tickCount * 0.3;
            for (int i = 0; i < 3; i++) {
                double ang = a + i * Math.PI * 2 / 3;
                level().addParticle(ParticleTypes.WITCH, getX() + Math.cos(ang) * 1.3, getY() + 1.2 + Math.sin(a * 0.5) * 0.6,
                        getZ() + Math.sin(ang) * 1.3, 0, 0, 0);
            }
        } else if (random.nextInt(8) == 0) {
            bodyParticle(ElementFx.dust(Element.ARCANE, 0.8F), 0.7, 0.8, 0);
        }
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
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 1.0F, 0.7F);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.65F;
    }
}
