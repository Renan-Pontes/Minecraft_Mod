package com.renanpontes.isekaimahou.feature.creatures_b.entity;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * Orc Berserker: abaixo de 50% de vida entra em FÚRIA — ruge, fica bem mais rápido e forte e solta vapor
 * vermelho. A fúria passa se ele se curar acima de 75%.
 */
public class OrcBerserkerEntity extends OrcEntity {
    private static final EntityDataAccessor<Boolean> RAGE = SynchedEntityData.defineId(OrcBerserkerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final ResourceLocation RAGE_SPEED = IsekaiMahou.id("berserker_rage_speed");
    private static final ResourceLocation RAGE_DAMAGE = IsekaiMahou.id("berserker_rage_damage");
    private static final ResourceLocation RAGE_KNOCKBACK = IsekaiMahou.id("berserker_rage_knockback");
    private static final DustParticleOptions RAGE_DUST = new DustParticleOptions(new Vector3f(0.85F, 0.08F, 0.05F), 1.3F);

    public OrcBerserkerEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return OrcEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 34.0)
                .add(Attributes.MOVEMENT_SPEED, 0.29)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.ARMOR, 2.0);
    }

    @Override
    protected String[] actionAnims() {
        return new String[] {"attack", "roar"};
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(RAGE, false);
    }

    public boolean isRaging() {
        return entityData.get(RAGE);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        float hp = getHealth() / getMaxHealth();
        if (!isRaging() && hp < 0.5F) enrage();
        else if (isRaging() && hp > 0.75F) calm();
        if (isRaging() && level() instanceof ServerLevel sl && tickCount % 3 == 0) {
            sl.sendParticles(RAGE_DUST, getX(), getY() + 1.2, getZ(), 2, 0.35, 0.6, 0.35, 0);
            if (tickCount % 15 == 0) sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 2.3, getZ(), 1, 0.2, 0.1, 0.2, 0);
        }
    }

    private void enrage() {
        entityData.set(RAGE, true);
        modifier(Attributes.MOVEMENT_SPEED, RAGE_SPEED, 0.45, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(Attributes.ATTACK_DAMAGE, RAGE_DAMAGE, 0.6, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(Attributes.ATTACK_KNOCKBACK, RAGE_KNOCKBACK, 0.8, AttributeModifier.Operation.ADD_VALUE);
        playAction("roar");
        playSound(SoundEvents.RAVAGER_ROAR, 1.2F, 1.35F);
        playSound(SoundEvents.PIGLIN_BRUTE_ANGRY, 1.5F, 0.7F);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 2.2, getZ(), 6, 0.5, 0.3, 0.5, 0);
            sl.sendParticles(RAGE_DUST, getX(), getY() + 1.0, getZ(), 30, 0.6, 0.8, 0.6, 0);
        }
        setBusy(12);
    }

    private void calm() {
        entityData.set(RAGE, false);
        removeMod(Attributes.MOVEMENT_SPEED, RAGE_SPEED);
        removeMod(Attributes.ATTACK_DAMAGE, RAGE_DAMAGE);
        removeMod(Attributes.ATTACK_KNOCKBACK, RAGE_KNOCKBACK);
    }

    private void modifier(Holder<Attribute> attr, ResourceLocation id, double v, AttributeModifier.Operation op) {
        AttributeInstance inst = getAttribute(attr);
        if (inst != null && !inst.hasModifier(id)) inst.addTransientModifier(new AttributeModifier(id, v, op));
    }

    private void removeMod(Holder<Attribute> attr, ResourceLocation id) {
        AttributeInstance inst = getAttribute(attr);
        if (inst != null) inst.removeModifier(id);
    }

    @Override
    protected float voicePitch() {
        return isRaging() ? 0.6F : 0.7F;
    }
}
