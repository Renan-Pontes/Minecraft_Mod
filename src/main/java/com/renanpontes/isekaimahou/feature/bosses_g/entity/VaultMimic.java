package com.renanpontes.isekaimahou.feature.bosses_g.entity;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Mímico do Cofre: um baú do tesouro que o Rei Mímico desperta. Pula e morde. Guarda o que havia no baú (itens ou a
 * tabela de loot ainda fechada) e devolve tudo ao morrer — matar os mímicos é saquear o cofre.
 */
public class VaultMimic extends Monster implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.vault_mimic.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.vault_mimic.walk");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.vault_mimic.death");
    private static final int MAX_STORED = 27;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final List<ItemStack> stored = new ArrayList<>();
    @Nullable
    private ResourceKey<LootTable> storedLoot;
    private int awakenTicks;

    public VaultMimic(EntityType<? extends VaultMimic> type, Level level) {
        super(type, level);
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, 28.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, VaultMimic.class, GBossEntity.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Guarda o conteúdo do baú que virou este mímico. */
    public void storeChest(List<ItemStack> items, @Nullable ResourceKey<LootTable> loot) {
        stored.clear();
        for (ItemStack s : items) if (!s.isEmpty() && stored.size() < MAX_STORED) stored.add(s.copy());
        storedLoot = loot;
    }

    /**
     * O Rei Mímico caiu ou voltou a dormir: este mímico volta a ser um baú no chão com o que guardava (itens e a tabela
     * de loot ainda fechada). Sem lugar livre, solta tudo no chão. Mímico sem tesouro (dos montes de ouro) só some.
     */
    public void revertToChest(ServerLevel sl) {
        if (isRemoved()) return;
        if (!stored.isEmpty() || storedLoot != null) {
            BlockPos spot = chestSpot(sl);
            if (spot != null) {
                Direction facing = Direction.fromYRot(getYRot()).getOpposite();
                sl.setBlock(spot, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), 3);
                if (sl.getBlockEntity(spot) instanceof ChestBlockEntity chest) {
                    int slot = 0;
                    for (ItemStack s : stored) {
                        if (slot >= chest.getContainerSize()) {
                            spawnAtLocation(s.copy());
                        } else {
                            chest.setItem(slot++, s.copy());
                        }
                    }
                    if (storedLoot != null) {
                        chest.setLootTable(storedLoot);
                        chest.setLootTableSeed(random.nextLong());
                    }
                    chest.setChanged();
                    stored.clear();
                    storedLoot = null;
                }
            }
            if (!stored.isEmpty() || storedLoot != null) dropStored(sl);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.CHEST_CLOSE, SoundSource.HOSTILE, 1.0F, 0.6F);
        }
        discard();
    }

    /** Chão livre embaixo do mímico (até 4 blocos), onde caiba um baú. */
    @Nullable
    private BlockPos chestSpot(ServerLevel sl) {
        BlockPos p = blockPosition();
        for (int i = 0; i < 5; i++) {
            BlockPos below = p.below();
            boolean free = sl.getBlockState(p).canBeReplaced() && sl.getFluidState(p).isEmpty();
            if (free && sl.getBlockState(below).isFaceSturdy(sl, below, Direction.UP)) return p;
            if (!free) return null;
            p = below;
        }
        return null;
    }

    private void dropStored(ServerLevel level) {
        for (ItemStack s : stored) spawnAtLocation(s.copy());
        stored.clear();
        if (storedLoot != null) {
            LootTable table = level.getServer().reloadableRegistries().getLootTable(storedLoot);
            LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, position())
                    .withOptionalParameter(LootContextParams.THIS_ENTITY, this).create(LootContextParamSets.CHEST);
            for (ItemStack s : table.getRandomItems(params)) spawnAtLocation(s);
            storedLoot = null;
        }
    }

    public void startAwaken() {
        awakenTicks = 16;
        triggerAnim("action", "awaken");
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || awakenTicks > 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (awakenTicks > 0) awakenTicks--;
        // Anda aos pulinhos.
        if (!level().isClientSide && onGround() && getDeltaMovement().horizontalDistanceSqr() > 0.002 && random.nextInt(6) == 0) {
            setDeltaMovement(getDeltaMovement().add(0, 0.32, 0));
            playSound(SoundEvents.CHEST_CLOSE, 0.4F, 1.4F + random.nextFloat() * 0.3F);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        triggerAnim("action", "bite");
        boolean ok = super.doHurtTarget(target);
        if (ok) playSound(SoundEvents.EVOKER_FANGS_ATTACK, 0.9F, 1.4F);
        return ok;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker != null && attacker != this && BgAllies.isAlly(this, attacker)) return false;
        return super.hurt(source, amount);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        dropStored(level);
        if (random.nextInt(3) == 0) spawnAtLocation(new ItemStack(Items.GOLD_NUGGET, 2 + random.nextInt(5)));
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (deathTime >= 20 && !level().isClientSide && !isRemoved()) {
            if (level() instanceof ServerLevel sl) {
                Vec3 c = position().add(0, 0.5, 0);
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.02);
            }
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /** Com tesouro dentro, o Pacífico não some com ele (o loot do baú se perderia). */
    @Override
    public boolean shouldDespawnInPeaceful() {
        return stored.isEmpty() && storedLoot == null;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.CHEST_LOCKED;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOOD_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.CHEST_CLOSE;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    // ------------------------------------------------------------------ GeckoLib

    private PlayState main(AnimationState<VaultMimic> state) {
        if (isDeadOrDying()) return state.setAndContinue(DEATH);
        return state.setAndContinue(state.isMoving() ? WALK : IDLE);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, this::main));
        controllers.add(new AnimationController<>(this, "action", 2, s -> PlayState.STOP)
                .triggerableAnim("bite", RawAnimation.begin().thenPlay("animation.vault_mimic.bite"))
                .triggerableAnim("awaken", RawAnimation.begin().thenPlay("animation.vault_mimic.awaken")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ------------------------------------------------------------------ Salvar

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ListTag list = new ListTag();
        for (ItemStack s : stored) {
            if (!s.isEmpty()) list.add(s.save(registryAccess()));
        }
        tag.put("Stored", list);
        if (storedLoot != null) tag.putString("StoredLoot", storedLoot.location().toString());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        stored.clear();
        for (Tag t : tag.getList("Stored", Tag.TAG_COMPOUND)) {
            ItemStack.parse(registryAccess(), t).ifPresent(stored::add);
        }
        ResourceLocation loot = ResourceLocation.tryParse(tag.getString("StoredLoot"));
        storedLoot = tag.contains("StoredLoot") && loot != null ? ResourceKey.create(Registries.LOOT_TABLE, loot) : null;
    }
}
