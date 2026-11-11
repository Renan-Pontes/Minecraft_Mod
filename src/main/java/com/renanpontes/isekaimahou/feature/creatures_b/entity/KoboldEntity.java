package com.renanpontes.isekaimahou.feature.creatures_b.entity;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Kobold: pequeno reptiliano minerador das cavernas. Anda em bando (um chama os outros), carrega picareta,
 * cava minérios expostos (se mobGriefing estiver ligado) e junta minérios/gemas do chão numa bolsa que
 * cai quando morre. Foge quando está quase morto.
 */
public class KoboldEntity extends CbMonster implements InventoryCarrier {
    private static final EntityDataAccessor<Boolean> MINING = SynchedEntityData.defineId(KoboldEntity.class, EntityDataSerializers.BOOLEAN);
    private final SimpleContainer pouch = new SimpleContainer(6);

    public KoboldEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setCanPickUpLoot(true);
        xpReward = 4;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 14.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    /** Só no subterrâneo (abaixo de y 50, sem céu) e no escuro. */
    public static boolean checkSpawn(EntityType<KoboldEntity> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                     RandomSource random) {
        if (MobSpawnType.isSpawner(reason) || reason == MobSpawnType.STRUCTURE) {
            return Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
        }
        return pos.getY() < 50 && !level.canSeeSky(pos) && Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
    }

    @Override
    public CbFaction faction() {
        return CbFaction.KOBOLD;
    }

    @Override
    protected String animId() {
        return "kobold";
    }

    @Override
    protected float voicePitch() {
        return 1.35F;
    }

    @Override
    protected double alertRadius() {
        return 14.0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MINING, false);
    }

    public boolean isMining() {
        return entityData.get(MINING);
    }

    @Override
    protected String loopAnim(boolean moving) {
        if (isMining()) return "mine";
        return super.loopAnim(moving);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, p -> getHealth() < getMaxHealth() * 0.3F, 10.0F, 1.0, 1.3,
                p -> !(p instanceof Player pl && (pl.isCreative() || pl.isSpectator()))));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, false));
        goalSelector.addGoal(3, new SeekLootGoal());
        goalSelector.addGoal(4, new MineOreGoal());
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.85));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        SpawnGroupData data) {
        SpawnGroupData d = super.finalizeSpawn(level, difficulty, reason, data);
        populateDefaultEquipmentSlots(level.getRandom(), difficulty);
        return d;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        float r = random.nextFloat();
        ItemStack pick = r < 0.15F ? new ItemStack(Items.IRON_PICKAXE)
                : r < 0.8F ? new ItemStack(Items.STONE_PICKAXE) : new ItemStack(Items.WOODEN_PICKAXE);
        setItemSlot(EquipmentSlot.MAINHAND, pick);
        setDropChance(EquipmentSlot.MAINHAND, 0.06F);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        // Bando: quem vê um jogador grita para os outros.
        if (tickCount % 20 == 0 && getTarget() instanceof Player p && p.isAlive()) alertFaction(p, 12.0);
    }

    // =====================================================================================
    //  Bolsa de minérios
    // =====================================================================================
    @Override
    public SimpleContainer getInventory() {
        return pouch;
    }

    public static boolean isTreasure(ItemStack stack) {
        return stack.is(Tags.Items.ORES) || stack.is(Tags.Items.RAW_MATERIALS) || stack.is(Tags.Items.GEMS)
                || stack.is(Tags.Items.NUGGETS) || stack.is(Items.COAL) || stack.is(Items.CHARCOAL) || stack.is(Items.FLINT);
    }

    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        return isTreasure(stack) && pouch.canAddItem(stack);
    }

    @Override
    protected void pickUpItem(ItemEntity item) {
        if (isTreasure(item.getItem())) {
            InventoryCarrier.pickUpItem(this, this, item);
            // Como no vanilla: quem pegou itens do chão não some (os minérios do jogador não se perdem).
            setPersistenceRequired();
        } else {
            super.pickUpItem(item);
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        for (ItemStack s : pouch.removeAllItems()) spawnAtLocation(s);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        writeInventoryToTag(tag, registryAccess());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        readInventoryFromTag(tag, registryAccess());
    }

    // =====================================================================================
    //  Sons
    // =====================================================================================
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.FOX_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.FOX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.FOX_DEATH;
    }

    // =====================================================================================
    //  Goals
    // =====================================================================================
    /** Vai buscar minérios/gemas caídos por perto. */
    private class SeekLootGoal extends Goal {
        private ItemEntity item;

        SeekLootGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (getTarget() != null || random.nextInt(10) != 0) return false;
            item = null;
            double best = 12 * 12;
            for (ItemEntity e : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(10, 3, 10),
                    e -> e.isAlive() && !e.hasPickUpDelay() && wantsToPickUp(e.getItem()))) {
                double d = distanceToSqr(e);
                if (d < best) {
                    best = d;
                    item = e;
                }
            }
            return item != null;
        }

        @Override
        public boolean canContinueToUse() {
            return item != null && item.isAlive() && getTarget() == null && !getNavigation().isDone();
        }

        @Override
        public void start() {
            getNavigation().moveTo(item, 1.1);
        }

        @Override
        public void stop() {
            item = null;
        }
    }

    /** Cava minérios expostos ao ar perto dele (precisa de picareta e de mobGriefing). */
    private class MineOreGoal extends Goal {
        private BlockPos ore;
        private int progress;
        private int duration;
        private int timeout;

        MineOreGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (getTarget() != null || random.nextInt(40) != 0) return false;
            if (!getMainHandItem().is(ItemTags.PICKAXES) || !EventHooks.canEntityGrief(level(), KoboldEntity.this)) return false;
            ore = findOre();
            return ore != null;
        }

        private BlockPos findOre() {
            BlockPos me = blockPosition();
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (BlockPos p : BlockPos.betweenClosed(me.offset(-8, -3, -8), me.offset(8, 4, 8))) {
                BlockState s = level().getBlockState(p);
                if (!s.is(Tags.Blocks.ORES) || !exposed(p)) continue;
                double d = p.distSqr(me);
                if (d < bestD) {
                    bestD = d;
                    best = p.immutable();
                }
            }
            return best;
        }

        private boolean exposed(BlockPos p) {
            for (Direction d : Direction.values()) {
                if (level().getBlockState(p.relative(d)).isAir()) return true;
            }
            return false;
        }

        @Override
        public void start() {
            progress = 0;
            timeout = 0;
            BlockState s = level().getBlockState(ore);
            duration = 30 + (int) (s.getDestroySpeed(level(), ore) * 12);
            getNavigation().moveTo(ore.getX() + 0.5, ore.getY(), ore.getZ() + 0.5, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return ore != null && getTarget() == null && timeout < 240 && level().getBlockState(ore).is(Tags.Blocks.ORES);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            // Nos ticks ímpares o GoalSelector chama tick() sem checar canContinueToUse: o minério pode já ter saído.
            if (ore == null) return;
            timeout++;
            getLookControl().setLookAt(ore.getX() + 0.5, ore.getY() + 0.5, ore.getZ() + 0.5);
            double dist = distanceToSqr(ore.getX() + 0.5, ore.getY() + 0.5, ore.getZ() + 0.5);
            if (dist > 6.5) {
                entityData.set(MINING, false);
                if (getNavigation().isDone()) getNavigation().moveTo(ore.getX() + 0.5, ore.getY(), ore.getZ() + 0.5, 1.0);
                return;
            }
            getNavigation().stop();
            entityData.set(MINING, true);
            progress++;
            ServerLevel sl = (ServerLevel) level();
            BlockState state = sl.getBlockState(ore);
            if (progress % 6 == 0) {
                swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                sl.playSound(null, ore, state.getSoundType(sl, ore, KoboldEntity.this).getHitSound(), SoundSource.HOSTILE, 0.6F, 1.1F);
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), ore.getX() + 0.5, ore.getY() + 0.5,
                        ore.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
            }
            sl.destroyBlockProgress(getId(), ore, Math.min(9, progress * 10 / Math.max(1, duration)));
            if (progress >= duration) {
                for (ItemStack drop : Block.getDrops(state, sl, ore, sl.getBlockEntity(ore), KoboldEntity.this, getMainHandItem())) {
                    ItemStack rest = pouch.addItem(drop);
                    if (!rest.isEmpty()) spawnAtLocation(rest);
                }
                sl.destroyBlock(ore, false, KoboldEntity.this);
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.2, getZ(), 3, 0.3, 0.2, 0.3, 0);
                ore = null;
            }
        }

        @Override
        public void stop() {
            if (ore != null && level() instanceof ServerLevel sl) sl.destroyBlockProgress(getId(), ore, -1);
            entityData.set(MINING, false);
            ore = null;
        }
    }
}
