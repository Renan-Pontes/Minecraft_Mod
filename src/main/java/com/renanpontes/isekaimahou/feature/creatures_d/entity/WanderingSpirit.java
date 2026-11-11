package com.renanpontes.isekaimahou.feature.creatures_d.entity;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ai.AbilityGoal;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ai.FlyWanderGoal;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ai.KeepRangeGoal;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Espírito Errante: alma arcana que vaga à noite por qualquer bioma, com uma lanterna de chama violeta.
 * Neutro e raro. Quem fica parado perto dele em paz por ~10 s ganha um presente (Essência Arcana) e o
 * espírito se desfaz. Atacado, revida com setas arcanas (magia de glifo do núcleo) e pisca para longe.
 * Armas comuns só causam metade do dano — magia o fere normalmente. Some ao amanhecer.
 */
public class WanderingSpirit extends FlyingElemental {
    private static final EntityDataAccessor<Integer> FADE = SynchedEntityData.defineId(WanderingSpirit.class, EntityDataSerializers.INT);
    private static final int FADE_TICKS = 60;
    private int calmTicks;
    private boolean gifted;
    private int blinkCooldown;

    public WanderingSpirit(EntityType<? extends WanderingSpirit> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.FLYING_SPEED, 0.35)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 20.0);
    }

    @Override
    public Element element() {
        return Element.ARCANE;
    }

    @Override
    protected String loopAnimation(boolean moving) {
        return moving ? "move" : "idle";
    }

    @Override
    protected String[] actionAnimations() {
        return new String[] {"cast"};
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FADE, -1);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new AbilityGoal(this, "cast", 2, 18, 16, 40, 70, true, this::arcaneBolt)
                .windup((t, left) -> {
                    if (left % 2 == 0) serverParticles(ParticleTypes.ENCHANT, localPoint(0.3, 1.3, 0.2), 6, 0.3, 0.5);
                }));
        goalSelector.addGoal(2, new KeepRangeGoal(this, 1.0, 6, 12, 2));
        goalSelector.addGoal(4, new FlyWanderGoal(this, 0.6, 1, 4, 10));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 10.0F, 1.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
    }

    private void arcaneBolt(LivingEntity target) {
        if (!MobMagic.cast(this, target, Element.ARCANE, 0.7F, "projectile", "arcane_damage")) {
            MobMagic.aim(this, target);
            ElementalBolt.shoot(this, target, Element.ARCANE, 4.0F, 1.0F, 2.0F, Items.ENDER_EYE);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Corpo etéreo: só a magia o fere por completo
        boolean magical = source.is(CoreRegistries.SPELL_DAMAGE) || source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        boolean r = super.hurt(source, magical ? amount : amount * 0.5F);
        if (r && !level().isClientSide && isAlive()) {
            gifted = true;
            calmTicks = 0;
            if (blinkCooldown <= 0 && random.nextFloat() < 0.35F) blink();
        }
        return r;
    }

    private void blink() {
        for (int i = 0; i < 10; i++) {
            double dx = (random.nextDouble() - 0.5) * 14;
            double dz = (random.nextDouble() - 0.5) * 14;
            double dy = random.nextInt(5) - 1;
            if (level().noCollision(this, getBoundingBox().move(dx, dy, dz))) {
                serverParticles(ParticleTypes.ENCHANT, position().add(0, 1, 0), 30, 0.4, 0.8);
                teleportTo(getX() + dx, getY() + dy, getZ() + dz);
                if (level() instanceof ServerLevel sl) ElementFx.burst(sl, position().add(0, 1, 0), Element.ARCANE, 14, 0.4);
                playSoundHere(SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.8F, 1.2F);
                blinkCooldown = 80;
                return;
            }
        }
    }

    public int fadeTicks() {
        return entityData.get(FADE);
    }

    private void startFade() {
        if (fadeTicks() < 0) entityData.set(FADE, FADE_TICKS);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !isAlive()) return;
        if (blinkCooldown > 0) blinkCooldown--;
        int fade = fadeTicks();
        if (fade >= 0) {
            if (fade == 0) {
                if (level() instanceof ServerLevel sl) {
                    ElementFx.burst(sl, position().add(0, 1, 0), Element.ARCANE, 30, 0.5);
                    sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 12, 0.3, 0.6, 0.3, 0.05);
                }
                playSoundHere(SoundEvents.SOUL_ESCAPE.value(), 1.2F, 0.8F);
                discard();
                return;
            }
            entityData.set(FADE, fade - 1);
        }
        // Amanhecer: espíritos errantes voltam ao além (exceto os que receberam nome ou estão lutando)
        if (!isPersistenceRequired() && getTarget() == null && level().isDay() && level().canSeeSky(blockPosition()) && random.nextInt(200) == 0) {
            startFade();
        }
        if (tickCount % 10 == 0 && !gifted && getTarget() == null && getLastHurtByMob() == null) {
            Player p = level().getNearestPlayer(this, 5.0);
            if (p != null && !p.isSpectator()) {
                calmTicks += 10;
                if (calmTicks >= 200) gift(p);
            } else {
                calmTicks = Math.max(0, calmTicks - 10);
            }
        }
    }

    /** Presente de quem teve paciência: Essência Arcana (e às vezes um Cristal de Mana). */
    private void gift(Player p) {
        gifted = true;
        playAction("cast");
        spawnAtLocation(new ItemStack(CoreRegistries.ESSENCES.get(Element.ARCANE).get()), 0.5F);
        if (random.nextInt(4) == 0) spawnAtLocation(new ItemStack(CoreRegistries.MANA_CRYSTAL.get()), 0.5F);
        if (level() instanceof ServerLevel sl) {
            ElementFx.line(sl, position().add(0, 1.2, 0), p.position().add(0, 1, 0), Element.ARCANE);
            sl.sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 0.8, 0.5, 0.6);
        }
        p.displayClientMessage(Component.translatable("message.isekai_mahou.wandering_spirit.gift").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        playSoundHere(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.7F);
        playSoundHere(SoundEvents.ALLAY_ITEM_GIVEN, 1.0F, 0.6F);
        startFade();
    }

    @Override
    public float renderAlpha(float partialTick) {
        float base = 0.72F + 0.1F * Mth.sin((tickCount + partialTick) * 0.08F);
        int fade = fadeTicks();
        return fade >= 0 ? base * Math.max(0, fade - partialTick) / FADE_TICKS : base;
    }

    @Override
    protected void clientEffects() {
        if (random.nextInt(2) == 0) {
            level().addParticle(ParticleTypes.ENCHANT, getRandomX(0.8), getY() + random.nextDouble() * 1.6, getRandomZ(0.8),
                    random.nextGaussian() * 0.2, 0.3, random.nextGaussian() * 0.2);
        }
        if (random.nextInt(4) == 0) {
            Vec3 lantern = localPoint(0.1, 0.45, 0.35);
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, lantern.x, lantern.y, lantern.z, 0, 0.01, 0);
        }
        if (random.nextInt(8) == 0) bodyParticle(ElementFx.dust(Element.ARCANE, 0.7F), 0.3, 0.4, -0.01);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Gifted", gifted);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        gifted = tag.getBoolean("Gifted");
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ALLAY_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ALLAY_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.5F;
    }
}
