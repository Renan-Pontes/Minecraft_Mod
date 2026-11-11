package com.renanpontes.isekaimahou.feature.creatures_a.entity.goblin;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;

/** Goblin arqueiro: atira com arco mantendo distância (anda de lado, recua se chegarem perto). */
public class GoblinArcher extends AbstractGoblin implements RangedAttackMob {
    public GoblinArcher(EntityType<? extends GoblinArcher> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes()
                .add(Attributes.MAX_HEALTH, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.ATTACK_DAMAGE, 2.0);
    }

    @Override
    protected void addCombatGoals() {
        goalSelector.addGoal(2, new RangedBowAttackGoal<>(this, 1.0, 30, 16.0F));
        // Sem arco (roubaram?): briga no soco mesmo.
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, false) {
            @Override
            public boolean canUse() {
                return !isHolding(s -> s.getItem() instanceof BowItem) && super.canUse();
            }
        });
    }

    @Override
    protected void equipGear(RandomSource random, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        ItemStack weapon = getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, item -> item instanceof BowItem));
        ItemStack ammo = getProjectile(weapon);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, ammo, velocity, weapon);
        if (weapon.getItem() instanceof ProjectileWeaponItem bow) arrow = bow.customArrow(arrow, ammo, weapon);
        double dx = target.getX() - getX();
        double dy = target.getY(0.3333333333333333) - arrow.getY();
        double dz = target.getZ() - getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        // Goblins miram pior que esqueletos.
        arrow.shoot(dx, dy + horiz * 0.2, dz, 1.5F, (float) (16 - level().getDifficulty().getId() * 4));
        playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.2F / (getRandom().nextFloat() * 0.4F + 0.8F));
        level().addFreshEntity(arrow);
    }

    @Override
    public boolean canFireProjectileWeapon(ProjectileWeaponItem weapon) {
        return weapon instanceof BowItem;
    }
}
