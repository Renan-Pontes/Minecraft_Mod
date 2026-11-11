package com.renanpontes.isekaimahou.feature.creatures_a.entity.goblin;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.creatures_a.CaRegistry;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Goblin guerreiro: porrete (ou adaga tosca), anda em bando. Às vezes um bando natural vem acompanhado
 * de um xamã ou de um hobgoblin, que vira o líder.
 */
public class Goblin extends AbstractGoblin {
    public Goblin(EntityType<? extends Goblin> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes()
                .add(Attributes.MAX_HEALTH, 14.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                // Base baixa: o porrete (+4) ou a adaga (+2) somam ao dano do mob (porrete ≈ 5,5, adaga ≈ 3,5).
                .add(Attributes.ATTACK_DAMAGE, 1.5)
                .add(Attributes.ARMOR, 1.0);
    }

    @Override
    protected void addCombatGoals() {
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, false));
    }

    @Override
    protected void equipGear(RandomSource random, DifficultyInstance difficulty) {
        boolean club = random.nextFloat() < 0.7F;
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(club ? CaRegistry.GOBLIN_CLUB.get() : CaRegistry.CRUDE_DAGGER.get()));
        setDropChance(EquipmentSlot.MAINHAND, 0.08F);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        if (spawnType != MobSpawnType.NATURAL || data != null) return result;
        // Primeiro do grupo natural: marca o grupo (os outros recebem a marca) e às vezes traz companhia.
        result = new PackMarker();
        if (level instanceof ServerLevel sl && random.nextFloat() < 0.25F) {
            EntityType<? extends AbstractGoblin> type = random.nextBoolean() ? CaRegistry.GOBLIN_SHAMAN.get() : CaRegistry.HOBGOBLIN.get();
            AbstractGoblin companion = type.create(sl);
            if (companion != null) {
                companion.moveTo(getX() + random.nextInt(3) - 1, getY(), getZ() + random.nextInt(3) - 1, random.nextFloat() * 360F, 0F);
                if (sl.noCollision(companion)) {
                    companion.finalizeSpawn(level, difficulty, MobSpawnType.REINFORCEMENT, null);
                    level.addFreshEntityWithPassengers(companion);
                }
            }
        }
        return result;
    }

    /** Marca de "grupo já iniciado" passada pelo gerador de spawn aos outros goblins do mesmo grupo. */
    private static final class PackMarker implements SpawnGroupData {
    }
}
