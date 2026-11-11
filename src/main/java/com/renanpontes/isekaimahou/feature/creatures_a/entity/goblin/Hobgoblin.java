package com.renanpontes.isekaimahou.feature.creatures_a.entity.goblin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Hobgoblin: o "sargento" do bando — maior, de armadura, espada/machado e escudo. Bloqueia parte dos golpes
 * vindos de frente (machados derrubam a guarda por uns segundos) e não foge.
 */
public class Hobgoblin extends AbstractGoblin {
    private int guardCooldown;

    public Hobgoblin(EntityType<? extends Hobgoblin> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                // Base baixa: a espada (+5) ou o machado de ferro (+8) somam ao dano do mob.
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.ARMOR_TOUGHNESS, 2.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override
    public int rank() {
        return 2;
    }

    @Override
    public boolean canFlee() {
        return false;
    }

    @Override
    protected void addCombatGoals() {
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true));
    }

    @Override
    protected void equipGear(RandomSource random, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(random.nextFloat() < 0.7F ? Items.IRON_SWORD : Items.IRON_AXE));
        setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        setDropChance(EquipmentSlot.MAINHAND, 0.06F);
        setDropChance(EquipmentSlot.OFFHAND, 0.06F);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Fogo amigo já é ignorado na base: sem som/animação de bloqueio para flechas de goblin.
        if (source.getEntity() instanceof AbstractGoblin) return super.hurt(source, amount);
        if (!level().isClientSide && guardCooldown <= 0 && canGuard(source) && random.nextFloat() < 0.4F) {
            if (source.getDirectEntity() instanceof LivingEntity attacker && attacker.getMainHandItem().getItem() instanceof AxeItem) {
                // Machado quebra a guarda: fica 5 s sem bloquear.
                guardCooldown = 100;
                playSound(SoundEvents.SHIELD_BREAK, 1.0F, 0.8F + random.nextFloat() * 0.4F);
                return super.hurt(source, amount);
            }
            guardCooldown = 15;
            playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 0.8F + random.nextFloat() * 0.4F);
            triggerAnim("action", "block");
            if (source.getDirectEntity() instanceof LivingEntity attacker) {
                attacker.knockback(0.5, getX() - attacker.getX(), getZ() - attacker.getZ());
            }
            return false;
        }
        return super.hurt(source, amount);
    }

    /** Golpe bloqueável: vem de frente, não ignora escudos e ele está segurando um escudo. */
    private boolean canGuard(DamageSource source) {
        if (!getOffhandItem().is(Items.SHIELD) || source.is(DamageTypeTags.BYPASSES_SHIELD)) return false;
        Vec3 from = source.getSourcePosition();
        if (from == null) return false;
        Vec3 view = calculateViewVector(0.0F, getYHeadRot());
        Vec3 to = from.vectorTo(position());
        to = new Vec3(to.x, 0.0, to.z).normalize();
        return to.dot(view) < 0.0;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (guardCooldown > 0) guardCooldown--;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("GuardCooldown", guardCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        guardCooldown = tag.getInt("GuardCooldown");
    }

    @Override
    protected float voiceBase() {
        return 1.05F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isAggressive() ? SoundEvents.PIGLIN_BRUTE_ANGRY : SoundEvents.PIGLIN_BRUTE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PIGLIN_BRUTE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PIGLIN_BRUTE_DEATH;
    }
}
