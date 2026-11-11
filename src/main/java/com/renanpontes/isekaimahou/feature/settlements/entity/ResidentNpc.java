package com.renanpontes.isekaimahou.feature.settlements.entity;

import java.util.EnumSet;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.settlements.Settlement;
import com.renanpontes.isekaimahou.feature.settlements.SettlementStyle;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Morador: chega quando há casas livres, passeia pela cidade de dia e volta para perto de casa à noite. Em guerra,
 * foge dos inimigos. Quatro visuais por estilo.
 */
public class ResidentNpc extends SettlementNpc {
    private static final Set<String> MALE_K = Set.of("satchel");
    private static final Set<String> FEMALE_K = Set.of("skirt", "hair_long");
    private static final Set<String> MALE_D = Set.of("hood", "satchel");
    private static final Set<String> FEMALE_D = Set.of("skirt", "hood");
    private static final Set<String> MALE_N = Set.of("beard", "satchel");
    private static final Set<String> FEMALE_N = Set.of("skirt", "hair_long");

    /** Casa (centro do prédio) onde dorme. */
    @Nullable private BlockPos house;

    public ResidentNpc(EntityType<? extends ResidentNpc> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    public Settlement.Role role() {
        return Settlement.Role.RESIDENT;
    }

    @Override
    protected int variantCount() {
        return 4;
    }

    @Override
    public Set<String> visibleBones() {
        boolean female = variant() % 2 == 1;
        return switch (style()) {
            case DESERT -> female ? FEMALE_D : MALE_D;
            case NORDIC -> female ? FEMALE_N : MALE_N;
            default -> female ? FEMALE_K : MALE_K;
        };
    }

    @Override
    protected int wanderRadius() {
        return 30;
    }

    public void setHouse(@Nullable BlockPos pos) {
        house = pos == null ? null : pos.immutable();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new FleeEnemiesGoal(this));
        goalSelector.addGoal(5, new GoHomeGoal());
    }

    @Override
    @Nullable
    protected Component ambientLine(Player near) {
        if (style() == SettlementStyle.NORDIC && random.nextInt(4) == 0) return line("resident.nordic", 3);
        if (style() == SettlementStyle.DESERT && random.nextInt(4) == 0) return line("resident.desert", 3);
        return super.ambientLine(near);
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (house != null) tag.put("House", net.minecraft.nbt.NbtUtils.writeBlockPos(house));
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        house = net.minecraft.nbt.NbtUtils.readBlockPos(tag, "House").orElse(null);
    }

    /** À noite, volta para a casa e fica por perto. */
    private final class GoHomeGoal extends Goal {
        GoHomeGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return house != null && level().isNight() && getTarget() == null
                    && distanceToSqr(house.getX() + 0.5, house.getY(), house.getZ() + 0.5) > 16.0 && random.nextInt(40) == 0;
        }

        @Override
        public void start() {
            if (house != null) getNavigation().moveTo(house.getX() + 0.5, house.getY() + 1, house.getZ() + 0.5, 0.7);
        }

        @Override
        public boolean canContinueToUse() {
            return !getNavigation().isDone() && level().isNight();
        }
    }
}
